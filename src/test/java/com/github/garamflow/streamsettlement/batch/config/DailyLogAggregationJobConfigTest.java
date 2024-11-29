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
import com.github.garamflow.streamsettlement.repository.stream.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Collection;
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

        // 데이터 초기화
        contentStatisticsRepository.deleteAll();
        viewLogRepository.deleteAll();
        advertisementContentPostRepository.deleteAll();
        contentPostRepository.deleteAll();
        memberRepository.deleteAll();

        // 테스트용 데이터 생성
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        createCompletedViewLogs(member, contentPost, LocalDate.now());
    }


    @BeforeEach
    void setUpJobLauncher() {
        jobLauncherTestUtils.setJob(dailyLogAggregationJob);
    }

    @Test
    void 일간_통계_집계_Job이_파티셔닝_정상_작동시_실행된다() throws Exception {
        // given
        LocalDate today = LocalDate.now();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", today.toString())
                .addString("gridSize", "5")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<ContentStatistics> statistics = contentStatisticsRepository.findAll();
        assertThat(statistics)
                .hasSize(1)
                .allSatisfy(stat -> {
                    assertThat(stat.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                    assertThat(stat.getViewCount()).isEqualTo(3L);
                    assertThat(stat.getWatchTime()).isGreaterThan(0L);
                });

        log.info("Job이 성공적으로 실행되었습니다. 총 통계 데이터 건수: {}", statistics.size());
    }

    @Test
    void 잘못된_Job_파라미터가_입력되면_예외가_발생한다() {
        // given
        JobParameters invalidParameters = new JobParametersBuilder()
                .addString("targetDate", "invalid-date") // 잘못된 날짜 형식
                .addString("gridSize", "5")
                .toJobParameters();

        // when & then
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(invalidParameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid 'targetDate' format");
    }


    @Test
    void 일간_통계_집계_Job이_정상적으로_실행된다() throws Exception {
        // given
        LocalDate today = LocalDate.now();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", today.toString()) // targetDate 전달
                .addString("gridSize", "5")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<ContentStatistics> statistics = contentStatisticsRepository.findAll();
        assertThat(statistics)
                .hasSize(1)
                .allSatisfy(stat -> {
                    assertThat(stat.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                    assertThat(stat.getViewCount()).isEqualTo(3L);
                    assertThat(stat.getWatchTime()).isGreaterThan(0L);
                });

        log.info("Job이 성공적으로 실행되었습니다. 총 통계 데이터 건수: {}", statistics.size());
    }

    @Test
    void 파티셔너가_올바르게_파티션을_생성한다() throws Exception {
        // given
        LocalDate today = LocalDate.now();
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        createCompletedViewLogs(member, contentPost, today);  // 테스트 데이터 생성
        
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", today.toString())
                .addString("gridSize", "5")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(
                "masterStep",
                jobParameters
        );

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        
        // 파티션 Step 실행 결과 확인
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions)
                .isNotEmpty()
                .hasSizeGreaterThanOrEqualTo(2); // masterStep과 최소 1개 이상의 workerStep

        // workerStep들의 실행 결과 확인
        List<StepExecution> workerSteps = stepExecutions.stream()
                .filter(execution -> execution.getStepName().startsWith("workerStep"))
                .toList();

        assertThat(workerSteps)
                .isNotEmpty()
                .allSatisfy(execution -> {
                    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
                    assertThat(execution.getReadCount()).isPositive();
                });
        
        // 로그 출력을 통한 디버깅
        log.info("Step Executions: {}", stepExecutions);
        stepExecutions.forEach(execution -> {
            log.info("Step Name: {}", execution.getStepName());
            log.info("Execution Context: {}", execution.getExecutionContext());
        });
    }


    private Member createMember() {
        String uniqueEmail = "test" + System.currentTimeMillis() + "@test.com";
        Member member = new Member.Builder()
                .email(uniqueEmail)
                .role(Role.MEMBER)
                .build();

        return memberRepository.save(member); // 영속 상태로 저장
    }

    private ContentPost createContentPost(Member member) {
        ContentPost contentPost = ContentPost.builder()
                .member(member)
                .title("테스트 영상")
                .url("http://test.com/video")
                .build();

        return contentPostRepository.save(contentPost);
    }

    private void createCompletedViewLogs(Member member, ContentPost contentPost, LocalDate date) {
        // 완료된 로그 3개 생성
        for (int i = 0; i < 3; i++) {
            DailyMemberViewLog viewLog = DailyMemberViewLog.builder()
                    .member(member)
                    .contentPost(contentPost)
                    .lastViewedPosition(100 * (i + 1))
                    .lastAdViewCount(i)
                    .logDate(date)  // 파라미터로 받은 날짜 사용
                    .status(StreamingStatus.COMPLETED)
                    .build();

            DailyMemberViewLog savedViewLog = viewLogRepository.save(viewLog);
            log.info("저장된 로그: {}", savedViewLog);
        }
    }
}