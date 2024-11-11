package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.listener.SettlementStepListener;
import com.github.garamflow.streamsettlement.batch.processor.SettlementProcessor;
import com.github.garamflow.streamsettlement.batch.reader.StatisticsReader;
import com.github.garamflow.streamsettlement.batch.writer.SettlementWriter;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.JobStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.job.DefaultJobParametersExtractor;
import org.springframework.batch.core.step.job.JobParametersExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

/**
 * 동영상 통계 및 정산을 처리하는 통합 배치 설정 클래스
 *
 * <p>두 개의 독립적인 Job 을 JobStep 으로 실행하여 처리합니다:</p>
 * <ol>
 *     <li>통계 Job (StatisticsJob)
 *         <ul>
 *             <li>시청 로그 기반의 통계 데이터 집계</li>
 *             <li>날짜별 파티셔닝으로 병렬 처리</li>
 *         </ul>
 *     </li>
 *     <li>정산 Job (SettlementJob)
 *         <ul>
 *             <li>집계된 통계를 기반으로 정산 처리</li>
 *             <li>각 Job 이 독립적으로 실행/재실행 가능</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p><b>전체 실행 순서:</b></p>
 * <pre>
 * 1. integratedJob() 실행
 *    ↓
 * 2. jobExecutionListener.beforeJob() 호출
 *    - 배치 작업 시작 시간 기록
 *    ↓
 * 3. statisticsJobStep() 실행 (첫 번째 JobStep)
 *    → statisticsJob() 실행
 *       → partitionStep() 실행 (Master Step)
 *          - Worker Step 병렬 실행 (날짜별 파티셔닝)
 *            - dailyLogReader.read()
 *            - compositeStatisticsProcessor.process()
 *            - statisticsWriter.write()
 *    ↓
 * 4. settlementJobStep() 실행 (두 번째 JobStep)
 *    → settlementJob() 실행
 *       → settlementStep() 실행
 *          - statisticsReader.read()
 *          - settlementProcessor.process()
 *          - settlementWriter.write()
 *    ↓
 * 5. jobExecutionListener.afterJob() 호출
 *    - 처리 결과 집계 및 로깅
 * </pre>
 *
 * <p><b>작업 단위별 독립성:</b></p>
 * <ul>
 *     <li>통계 Job: 파티셔닝을 통한 병렬 처리</li>
 *     <li>정산 Job: 통계 처리 완료 후 독립 실행</li>
 *     <li>각 Job 은 실패 시 개별 재실행 가능</li>
 * </ul>
 *
 * <p><b>파티셔닝 처리:</b></p>
 * <ul>
 *     <li>통계 집계 Step 이 날짜별로 파티셔닝되어 병렬 처리됨</li>
 *     <li>기본 4개의 스레드로 병렬 처리 (설정으로 변경 가능)</li>
 *     <li>각 Worker Step 이 독립적으로 자신의 날짜 데이터만 처리</li>
 * </ul>
 *
 * <p><b>데이터 처리 흐름:</b></p>
 * <pre>
 * 시청 로그(DailyMemberViewLog) - 파티셔닝하여 병렬 처리
 * → 통계 데이터(ContentStatistics)
 * → 정산 데이터(Settlement)
 * </pre>
 *
 * <p><b>사용하는 테이블:</b></p>
 * <ul>
 *     <li>daily_user_view_log: 사용자 시청 로그</li>
 *     <li>content_statistics: 기간별 통계 데이터</li>
 *     <li>settlement: 정산 결과</li>
 * </ul>
 *
 * <p><b>청크 처리:</b></p>
 * <ul>
 *     <li>각 Step 은 설정된 크기(기본 100건)로 청크 처리</li>
 *     <li>청크 단위로 트랜잭션 관리</li>
 *     <li>실패 시 청크 단위 롤백</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class IntegratedBatchConfig {

    private static final String STATISTICS_COUNT_KEY = "statisticsCount";
    private final BatchProperties batchProperties;

    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;
    private final PlatformTransactionManager transactionManager;

    private final StatisticsPartitionConfig statisticsPartitionConfig;

    private final StatisticsReader statisticsReader;
    private final SettlementProcessor settlementProcessor;
    private final SettlementWriter settlementWriter;
    private final SettlementStepListener settlementStepListener;


    /**
     * 통계와 정산을 순차적으로 처리하는 통합 Job 을 구성합니다.
     * 각각의 처리가 독립적인 Job 으로 실행됩니다.
     *
     * <p><b>실행 순서:</b></p>
     * <ol>
     *     <li>JobExecutionListener - 전체 작업 시작</li>
     *     <li>StatisticsJob (JobStep 으로 실행)
     *         <ul>
     *             <li>날짜별 파티셔닝으로 통계 데이터 집계</li>
     *             <li>실패 시 Statistics Job 만 재실행 가능</li>
     *         </ul>
     *     </li>
     *     <li>SettlementJob (JobStep 으로 실행)
     *         <ul>
     *             <li>집계된 통계 기반으로 정산 처리</li>
     *             <li>실패 시 Settlement Job 만 재실행 가능</li>
     *         </ul>
     *     </li>
     * </ol>
     */
    @Bean
    public Job integratedJob() {
        return new JobBuilder("integratedStatisticsAndSettlementJob", jobRepository)
                .listener(jobExecutionListener())
                .start(statisticsJobStep())    // Statistics Job 을 실행하는 Step
                .next(settlementJobStep())     // Settlement Job 을 실행하는 Step
                .build();
    }

    /**
     * 통계 집계를 담당하는 Job 을 Step 으로 실행합니다.
     *
     * <p><b>주요 기능:</b></p>
     * <ul>
     *     <li>파티셔닝된 통계 집계 Job 을 독립적으로 실행</li>
     *     <li>Job 파라미터를 하위 Job 으로 전달</li>
     *     <li>실패 시 이 Job 만 재실행 가능</li>
     * </ul>
     */
    @Bean
    public Step statisticsJobStep() {
        return new JobStepBuilder(new StepBuilder("statisticsJobStep", jobRepository))
                .job(statisticsJob())
                .launcher(jobLauncher)
                .parametersExtractor(jobParametersExtractor())
                .build();
    }

    /**
     * 통계 집계를 위한 Job 을 구성합니다.
     * 파티셔닝된 Step 을 포함합니다.
     */
    @Bean
    public Job statisticsJob() {
        return new JobBuilder("statisticsJob", jobRepository)
                .start(statisticsPartitionConfig.partitionStep())
                .build();
    }

    /**
     * 정산 처리를 담당하는 Job 을 Step 으로 실행합니다.
     *
     * <p><b>주요 기능:</b></p>
     * <ul>
     *     <li>정산 처리 Job 을 독립적으로 실행</li>
     *     <li>통계 Job 의 결과를 참조하여 처리</li>
     *     <li>실패 시 이 Job 만 재실행 가능</li>
     * </ul>
     */
    @Bean
    public Step settlementJobStep() {
        return new JobStepBuilder(new StepBuilder("settlementJobStep", jobRepository))
                .job(settlementJob())
                .launcher(jobLauncher)
                .parametersExtractor(jobParametersExtractor())
                .build();
    }

    /**
     * 정산 처리를 위한 Job 을 구성합니다.
     */
    @Bean
    public Job settlementJob() {
        return new JobBuilder("settlementJob", jobRepository)
                .start(settlementStep())
                .build();
    }

    /**
     * 집계된 통계 데이터를 기반으로 정산을 처리하는 Step 을 구성합니다.
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>Reader: 집계된 통계 데이터 조회 (StatisticsReader)</li>
     *     <li>Processor: 조회수와 광고에 따른 정산 금액 계산 (SettlementProcessor)</li>
     *     <li>Writer: 계산된 정산 데이터 저장 (SettlementWriter)</li>
     * </ol>
     *
     * <p>100건 단위로 청크 처리를 수행합니다.</p>
     *
     * @return 구성된 정산 처리 Step
     */
    private Step settlementStep() {
        return new StepBuilder("settlement", jobRepository)
                .<ContentStatistics, Settlement>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(statisticsReader)          // 집계된 통계 읽기
                .processor(settlementProcessor)    // 정산 금액 계산
                .writer(settlementWriter)          // 정산 결과 저장
                .listener(settlementStepListener)
                .build();
    }

    /**
     * 배치 작업의 시작과 종료 시점에 대한 처리를 담당하는 리스너를 생성합니다.
     *
     * <p><b>주요 기능:</b></p>
     * <ul>
     *     <li>작업 시작 시간 기록</li>
     *     <li>처리된 통계 및 정산 건수 집계</li>
     *     <li>총 실행 시간 계산</li>
     *     <li>작업 결과 상태 로깅</li>
     * </ul>
     *
     * @return 구성된 Job 실행 리스너
     */
    @Bean
    @NonNull
    public JobExecutionListener jobExecutionListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(@NonNull JobExecution jobExecution) {
                log.info("Starting integrated batch job at: {}", LocalDateTime.now());
                // 작업 시작 시간 및 초기 데이터 설정
                jobExecution.getExecutionContext().put("startTime", System.currentTimeMillis());
            }

            @Override
            public void afterJob(@NonNull JobExecution jobExecution) {
                long startTime = jobExecution.getExecutionContext().getLong("startTime");
                long endTime = System.currentTimeMillis();
                log.info("Integrated batch job completed at: {}. Total time: {} seconds",
                        LocalDateTime.now(), (endTime - startTime) / 1000);

                // 작업 결과 요약 로깅
                if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
                    log.info("Job completed successfully - Statistics processed: {}, Settlements created: {}",
                            jobExecution.getExecutionContext().get(STATISTICS_COUNT_KEY),
                            jobExecution.getExecutionContext().get("settlementCount"));
                } else {
                    log.error("Job failed with status: {}", jobExecution.getStatus());
                }
            }
        };
    }

    /**
     * Job 파라미터 전달을 위한 Extractor 를 구성합니다.
     * 상위 Job 의 파라미터를 하위 Job 으로 전달합니다.
     */
    @Bean
    public JobParametersExtractor jobParametersExtractor() {
        DefaultJobParametersExtractor extractor = new DefaultJobParametersExtractor();
        extractor.setKeys(new String[]{"date"});
        return extractor;
    }
}
