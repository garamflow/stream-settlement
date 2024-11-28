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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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

    private LocalDate today;
    private ContentPost contentPost;
    private Member member;

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
        member = createMember();
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
                createContentStatistics(200L, 2L)
        );

        // when
        writer.write(new Chunk<>(statistics));

        // then
        List<ContentStatistics> saved = contentStatisticsRepository.findAll();
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
        List<ContentStatistics> statistics = Arrays.asList(
                createContentStatistics(100L, 1L),
                createContentStatisticsWithDate(200L, 2L, today.minusDays(1))
        );

        // when
        writer.write(new Chunk<>(statistics));

        // then
        List<ContentStatistics> saved = contentStatisticsRepository.findAll();
        assertThat(saved).hasSize(2)
                .satisfies(stats -> {
                    assertThat(stats).extracting(ContentStatistics::getStatisticsDate)
                            .containsExactlyInAnyOrder(today, today.minusDays(1));
                    assertThat(stats).extracting(ContentStatistics::getWatchTime)
                            .containsExactlyInAnyOrder(100L, 200L);
                    assertThat(stats).extracting(ContentStatistics::getViewCount)
                            .containsExactlyInAnyOrder(1L, 2L);
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
        writer.write(new Chunk<>(statistics));

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
                .isThrownBy(() -> writer.write(new Chunk<>(emptyStatistics)));
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
        writer.write(new Chunk<>(statistics));

        // then
        List<ContentStatistics> saved = contentStatisticsRepository.findAll();
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
        return createContentStatisticsWithDate(watchTime, viewCount, today);
    }

    private ContentStatistics createContentStatisticsWithDate(
            long watchTime,
            long viewCount,
            LocalDate statisticsDate) {
        return new ContentStatistics.Builder()
                .contentPost(contentPost)
                .statisticsDate(statisticsDate)
                .period(StatisticsPeriod.DAILY)
                .watchTime(watchTime)
                .viewCount(viewCount)
                .accumulatedViews(contentPost.getTotalViews())
                .build();
    }
}