package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.partition.DailyLogPartitioner;
import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.advertisement.AdvertisementContentPostRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.log.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
@SpringBatchTest
class DailyLogAggregationJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DailyMemberViewLogRepository viewLogRepository;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private AdvertisementContentPostRepository advertisementContentPostRepository;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier("dailyLogAggregationJob")
    private Job dailyLogAggregationJob;

    @Autowired
    private DailyLogPartitioner partitioner;

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        contentStatisticsRepository.deleteAll();
        viewLogRepository.deleteAll();
        advertisementContentPostRepository.deleteAll();
        contentPostRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 일간_주간_월간_통계_집계_Job이_정상적으로_실행된다() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        // 데이터가 없는 경우를 테스트하기 위해 데이터베이스를 비웁니다.
        viewLogRepository.deleteAll();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("gridSize", "2")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = null;
        try {
            jobExecution = jobLauncherTestUtils.launchJob(jobParameters);
        } catch (EmptyResultDataAccessException e) {
            // 데이터가 없는 경우 예외가 발생할 수 있습니다.
            log.warn("No data found for target date: {}", targetDate, e);
        }

        // then
        assertThat(jobExecution).isNotNull();
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 데이터가 없는 경우에도 통계 데이터가 없는지 확인합니다.
        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndStatisticsDate(
                StatisticsPeriod.DAILY, targetDate);
        assertThat(dailyStats).isEmpty();
    }

    @Test
    void 데이터가_있는_경우_통계가_정상적으로_집계된다() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        LocalDate targetDate = LocalDate.now();
        createViewLogs(member, contentPost, targetDate, 5);  // 5개의 로그 생성

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("gridSize", "2")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndStatisticsDate(
                StatisticsPeriod.DAILY, targetDate);
        assertThat(dailyStats).isNotEmpty();
        assertThat(dailyStats.get(0).getViewCount()).isEqualTo(5);
    }

    @Test
    void 잘못된_날짜_형식으로_Job을_실행하면_실패한다() throws Exception {
        // given
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", "invalid-date")
                .addString("gridSize", "2")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when & then
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid 'targetDate' format");
    }

    @Test
    void targetDate_파라미터가_없으면_실패한다() throws Exception {
        // given
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("gridSize", "2")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when & then
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
                .isInstanceOf(JobParametersInvalidException.class)
                .hasMessageContaining("The JobParameters do not contain required keys: [targetDate]");
    }

    @Test
    void 데이터가_없는_날짜로_Job을_실행하면_정상_완료된다() throws Exception {
        // given
        LocalDate emptyDate = LocalDate.now().plusDays(1); // 미래 날짜로 테스트
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", emptyDate.toString())
                .addString("gridSize", "2")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndStatisticsDate(
                StatisticsPeriod.DAILY, emptyDate);
        assertThat(dailyStats).isEmpty();
    }

    @Test
    void 단일_파티션으로_데이터를_처리할_수_있다() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        LocalDate targetDate = LocalDate.now();
        createViewLogs(member, contentPost, targetDate, 100);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("gridSize", "1")  // 단일 파티션으로 테스트
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndStatisticsDate(
                StatisticsPeriod.DAILY, targetDate);
        assertThat(dailyStats).isNotEmpty();
        assertThat(dailyStats.get(0).getViewCount()).isEqualTo(100);
    }

    @Test
//    @Disabled("실제 운영 환경에서만 멀티 파티션 테스트 수행")
    void 멀티_파티션_테스트() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        LocalDate targetDate = LocalDate.now();
        createViewLogs(member, contentPost, targetDate, 100);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("gridSize", "4")  // 멀티 파티션으로 테스트
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndStatisticsDate(
                StatisticsPeriod.DAILY, targetDate);
        assertThat(dailyStats).isNotEmpty();
        assertThat(dailyStats.get(0).getViewCount()).isEqualTo(100);
    }

    @Test
    void 과거_날짜의_데이터도_처리할_수_있다() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        LocalDate pastDate = LocalDate.now().minusDays(7);
        createViewLogs(member, contentPost, pastDate, 3);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", pastDate.toString())
                .addString("gridSize", "1")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        List<ContentStatistics> dailyStats = contentStatisticsRepository.findByPeriodAndStatisticsDate(
                StatisticsPeriod.DAILY, pastDate);
        assertThat(dailyStats).isNotEmpty();
        assertThat(dailyStats.get(0).getViewCount()).isEqualTo(3);
    }

    private Member createMember() {
        String uniqueEmail = "test" + System.currentTimeMillis() + "@test.com";
        Member member = new Member.Builder()
                .email(uniqueEmail)
                .role(Role.MEMBER)
                .build();
        return memberRepository.save(member);
    }

    private ContentPost createContentPost(Member member) {
        ContentPost contentPost = ContentPost.builder()
                .member(member)
                .title("테스트 영상")
                .url("http://test.com/video")
                .build();
        return contentPostRepository.save(contentPost);
    }

    private List<DailyMemberViewLog> createViewLogs(Member member, ContentPost contentPost, LocalDate date, int count) {
        List<DailyMemberViewLog> logs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyMemberViewLog viewLog = DailyMemberViewLog.builder()
                    .member(member)
                    .contentPost(contentPost)
                    .lastViewedPosition(100 * (i + 1))
                    .lastAdViewCount(i)
                    .logDate(date)
                    .status(StreamingStatus.COMPLETED)
                    .build();
            logs.add(viewLogRepository.save(viewLog));
        }
        return logs;
    }
}