package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentStatus;
import com.github.garamflow.streamsettlement.repository.advertisement.AdvertisementContentPostRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.stream.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@SpringBatchTest
@Transactional
class DailyLogWriterTest {

    @Autowired
    private DailyLogWriter writer;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private DailyMemberViewLogRepository dailyMemberViewLogRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AdvertisementContentPostRepository advertisementContentPostRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private ContentPost contentPost;

    @BeforeEach
    void setUp() {
        // Step Context 설정
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepSynchronizationManager.register(stepExecution);  // StepScope 활성화

        // 데이터 정리 (순서 중요)
        dailyMemberViewLogRepository.deleteAll();  // 먼저 자식 테이블 정리
        contentStatisticsRepository.deleteAll();
        advertisementContentPostRepository.deleteAll();
        contentPostRepository.deleteAll();
        memberRepository.deleteAll();

        // 테스트 데이터 준비
        today = LocalDate.now();
        Member member = createMember();
        contentPost = createContentPost(member);
    }

    @AfterEach
    void tearDown() {
        StepSynchronizationManager.release();  // StepScope 정리
    }

    @Test
    void 같은_컨텐츠의_통계를_합산하여_저장한다() throws Exception {
        // given
        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(100L, 1L),
                createContentStatistics(100L, 1L),
                createContentStatistics(100L, 1L)
        );

        // when
        writer.write(new Chunk<>(Collections.singletonList(statistics)));

        // then
        List<ContentStatistics> saved = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.DAILY, today);
        assertThat(saved).hasSize(1)
                .first()
                .satisfies(stat -> {
                    assertThat(stat.getWatchTime()).isEqualTo(300L);
                    assertThat(stat.getViewCount()).isEqualTo(3L);
                    assertThat(stat.getContentPost()).isEqualTo(contentPost);
                    assertThat(stat.getStatisticsDate()).isEqualTo(today);
                    assertThat(stat.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                });

        // ContentPost의 누적 시청 시간도 확인
        ContentPost updatedPost = contentPostRepository.findById(contentPost.getId()).orElseThrow();
        assertThat(updatedPost.getTotalWatchTime()).isEqualTo(300L);
    }

    @Test
    void 서로_다른_날짜의_통계는_별도로_저장된다() throws Exception {
        // given
        LocalDate yesterday = today.minusDays(1);
        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(100L, 1L),  // today
                createContentStatisticsWithDate(200L, 1L, yesterday)  // yesterday
        );

        // when
        writer.write(new Chunk<>(Collections.singletonList(statistics)));

        // then
        List<ContentStatistics> todayStats = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.DAILY, today);
        List<ContentStatistics> yesterdayStats = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.DAILY, yesterday);

        assertThat(todayStats).hasSize(1)
                .first()
                .satisfies(stat -> {
                    assertThat(stat.getWatchTime()).isEqualTo(100L);
                    assertThat(stat.getViewCount()).isEqualTo(1L);
                    assertThat(stat.getStatisticsDate()).isEqualTo(today);
                });

        assertThat(yesterdayStats).hasSize(1)
                .first()
                .satisfies(stat -> {
                    assertThat(stat.getWatchTime()).isEqualTo(200L);
                    assertThat(stat.getViewCount()).isEqualTo(1L);
                    assertThat(stat.getStatisticsDate()).isEqualTo(yesterday);
                });
    }

    @Test
    void 컨텐츠의_누적_시청시간이_정상적으로_업데이트된다() throws Exception {
        // given
        long initialWatchTime = 100L;
        contentPost.addWatchTime(initialWatchTime);  // 초기 시청 시간 설정
        contentPostRepository.save(contentPost);

        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(150L, 1L),
                createContentStatistics(250L, 1L)
        );

        // when
        writer.write(new Chunk<>(Collections.singletonList(statistics)));

        // then
        ContentPost updatedPost = contentPostRepository.findById(contentPost.getId()).orElseThrow();
        assertThat(updatedPost.getTotalWatchTime())
                .isEqualTo(initialWatchTime + 150L + 250L);  // 초기값 + 새로운 시청시간들의 합
    }

    @Test
    void 빈_청크는_정상적으로_처리된다() throws Exception {
        // given
        List<ContentStatistics> emptyStatistics = List.of();

        // when & then
        assertThatNoException()
                .isThrownBy(() -> writer.write(new Chunk<>(Collections.singletonList(emptyStatistics))));
    }

    @Test
    void 동일_컨텐츠_동일_날짜의_통계는_누적된다() throws Exception {
        // given
        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(100L, 1L),
                createContentStatistics(100L, 1L),
                createContentStatistics(100L, 1L)
        );

        // when
        writer.write(new Chunk<>(Collections.singletonList(statistics)));

        // then
        List<ContentStatistics> saved = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.DAILY, today);
        assertThat(saved).hasSize(1)
                .first()
                .satisfies(stat -> {
                    assertThat(stat.getWatchTime()).isEqualTo(300L);
                    assertThat(stat.getViewCount()).isEqualTo(3L);
                    assertThat(stat.getContentPost()).isEqualTo(contentPost);
                    assertThat(stat.getStatisticsDate()).isEqualTo(today);
                    assertThat(stat.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                });

        // ContentPost의 누적 시청 시간도 확인
        ContentPost updatedPost = contentPostRepository.findById(contentPost.getId()).orElseThrow();
        assertThat(updatedPost.getTotalWatchTime()).isEqualTo(300L);
    }


    @Test
    void 동기화된_누적_시청시간_업데이트가_정상적으로_작동한다() throws Exception {
        // given
        long initialWatchTime = 100L;
        contentPost.addWatchTime(initialWatchTime); // 초기 누적 시청 시간 설정
        contentPostRepository.save(contentPost);

        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(150L, 1L),
                createContentStatistics(250L, 1L)
        );

        // when
        writer.write(new Chunk<>(Collections.singletonList(statistics)));

        // then
        ContentPost updatedPost = contentPostRepository.findById(contentPost.getId()).orElseThrow();
        assertThat(updatedPost.getTotalWatchTime())
                .isEqualTo(initialWatchTime + 150L + 250L); // 초기값 + 새로운 시청시간 합산
    }

    @Test
    void 빈_청크_입력시_예외없이_처리된다() {
        // given
        List<ContentStatistics> emptyStatistics = List.of();

        // when & then
        assertThatNoException()
                .isThrownBy(() -> writer.write(new Chunk<>(Collections.singletonList(emptyStatistics))));
    }

    @Test
    void 일간_통계와_상위_기간_통계가_정상적으로_저장된다() throws Exception {
        // given
        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(100L, 1L),
                createContentStatistics(200L, 1L)
        );

        // when
        writer.write(new Chunk<>(Collections.singletonList(statistics)));

        // then
        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.DAILY, today);
        List<ContentStatistics> weeklyStats = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.WEEKLY, today);
        List<ContentStatistics> monthlyStats = contentStatisticsRepository.findByPeriodAndDate(StatisticsPeriod.MONTHLY, today);

        assertThat(dailyStats).hasSize(1);
        assertThat(weeklyStats).hasSize(1);
        assertThat(monthlyStats).hasSize(1);

        ContentStatistics dailyStat = dailyStats.get(0);
        assertThat(dailyStat.getWatchTime()).isEqualTo(300L);
        assertThat(dailyStat.getViewCount()).isEqualTo(2L);
    }

    @Test
    void 통계_정합성_검증이_실패하면_경고_로그가_출력된다() throws Exception {
        // given
        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(100L, 1L),
                createContentStatistics(200L, 1L)
        );

        // 일부러 정합성이 맞지 않는 데이터 추가
        contentStatisticsRepository.save(
                new ContentStatistics.Builder()
                        .contentPost(contentPost)
                        .statisticsDate(today)
                        .period(StatisticsPeriod.WEEKLY)
                        .viewCount(10L) // 일간 통계와 맞지 않는 값
                        .watchTime(1000L)
                        .build()
        );

        // when & then
        assertThatCode(() -> writer.write(new Chunk<>(Collections.singletonList(statistics))))
                .doesNotThrowAnyException();
    }

    @Test
    void 컨텐츠가_null이면_예외를_던진다() throws Exception {
        // given
        ContentStatistics invalidStats = ContentStatistics.builder()
                .contentPost(null)
                .statisticsDate(LocalDate.now())
                .period(StatisticsPeriod.DAILY)
                .watchTime(100L)
                .viewCount(1L)
                .build();

        // when & then
        assertThatThrownBy(() -> writer.write(new Chunk<>(Collections.singletonList(List.of(invalidStats)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("컨텐츠가 null입니다");
    }

    private Member createMember() {
        String uniqueEmail = "test" + System.currentTimeMillis() + "@test.com";
        Member member = new Member.Builder()
                .email(uniqueEmail)
                .username("테스트 유저")
                .role(Role.CREATOR)
                .build();
        return memberRepository.save(member);
    }

    private ContentPost createContentPost(Member member) {
        ContentPost post = ContentPost.builder()
                .member(member)
                .title("테스트 영상")
                .description("테스트 설명")
                .url("http://test.com/video")
                .status(ContentStatus.ACTIVE)
                .build();
        return contentPostRepository.save(post);
    }

    private ContentStatistics createContentStatistics(long watchTime, long viewCount) {
        return new ContentStatistics.Builder()
                .contentPost(contentPost)
                .statisticsDate(today)
                .period(StatisticsPeriod.DAILY)
                .watchTime(watchTime)
                .viewCount(viewCount)
                .build();
    }

    private ContentStatistics createContentStatisticsWithDate(long watchTime, long viewCount, LocalDate date) {
        return new ContentStatistics.Builder()
                .contentPost(contentPost)
                .statisticsDate(date)
                .period(StatisticsPeriod.DAILY)
                .watchTime(watchTime)
                .viewCount(viewCount)
                .build();
    }

    private List<ContentStatistics> findStatisticsByPeriod(StatisticsPeriod period) {
        String sql = "SELECT * FROM content_statistics WHERE period = ?";
        return jdbcTemplate.query(sql, new Object[]{period.name()}, (rs, rowNum) ->
                new ContentStatistics.Builder()
                        .contentPost(contentPost)
                        .statisticsDate(rs.getDate("statistics_date").toLocalDate())
                        .period(StatisticsPeriod.valueOf(rs.getString("period")))
                        .watchTime(rs.getLong("watch_time"))
                        .viewCount(rs.getLong("view_count"))
                        .build()
        );
    }
}