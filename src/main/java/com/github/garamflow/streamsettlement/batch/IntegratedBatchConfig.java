package com.github.garamflow.streamsettlement.batch;

import com.github.garamflow.streamsettlement.batch.config.StatisticsPartitionConfig;
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
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;

/**
 * 동영상 통계 및 정산을 처리하는 통합 배치 설정 클래스
 *
 * <p>이 클래스는 다음 두 가지 주요 처리를 순차적으로 실행합니다:</p>
 * <ol>
 *     <li>시청 로그 기반의 통계 데이터 집계 (파티셔닝 처리)</li>
 *     <li>집계된 통계를 기반으로 한 정산 처리</li>
 * </ol>
 *
 * <p><b>전체 실행 순서:</b></p>
 * <pre>
 * 1. integratedJob() 실행
 *    ↓
 * 2. jobExecutionListener.beforeJob() 호출
 *    - 배치 작업 시작 시간 기록
 *    ↓
 * 3. aggregateAllStatisticsStep() 실행 (Master Step)
 *    3.1 각 Worker Step 병렬 실행 (날짜별 파티셔닝)
 *        - 2024-04-08 데이터 처리
 *        - 2024-04-09 데이터 처리
 *        - 2024-04-10 데이터 처리
 *        - 2024-04-11 데이터 처리
 *        각 Worker Step 은 다음 과정을 수행:
 *        - dailyLogReader.read() - 청크 단위로 반복
 *        - compositeStatisticsProcessor.process()
 *        - statisticsWriter.write()
 *    3.2 모든 Worker Step 완료 대기
 *    3.3 statisticsStepListener.afterStep()
 *    ↓
 * 4. settlementStep() 실행
 *    4.1 settlementStepListener.beforeStep()
 *        - 통계 처리 완료 여부 확인
 *    4.2 statisticsReader.read() - 청크 단위로 반복
 *    4.3 settlementProcessor.process()
 *    4.4 settlementWriter.write()
 *    4.5 settlementStepListener.afterStep()
 *    ↓
 * 5. jobExecutionListener.afterJob() 호출
 *    - 처리 결과 집계 및 로깅
 * </pre>
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
 *
 * <p><b>설정:</b></p>
 * <ul>
 *     <li>spring.batch.partition.pool-size: 병렬 처리 스레드 수</li>
 *     <li>spring.batch.chunk-size: 청크 단위 크기</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class IntegratedBatchConfig {

    private static final String STATISTICS_COUNT_KEY = "statisticsCount";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final StatisticsPartitionConfig statisticsPartitionConfig;

    private final StatisticsReader statisticsReader;
    private final SettlementProcessor settlementProcessor;
    private final SettlementWriter settlementWriter;
    private final SettlementStepListener settlementStepListener;


    /**
     * 통합 배치 작업을 정의하는 Job 을 생성합니다.
     *
     * <p><b>실행 순서:</b></p>
     * <ol>
     *     <li>JobExecutionListener 설정 - 배치 실행 전후 처리</li>
     *     <li>통계 집계 스텝 실행 (aggregateAllStatisticsStep)</li>
     *     <li>정산 처리 스텝 실행 (settlementStep)</li>
     * </ol>
     *
     * @return 구성된 배치 Job
     */
    @Bean
    public Job integratedJob() {
        return new JobBuilder("integratedStatisticsAndSettlementJob", jobRepository)
                .listener(jobExecutionListener())
                .start(aggregateAllStatisticsStep())    // 통계 집계 스텝
                .next(settlementStep())                 // 정산 처리 스텝
                .build();
    }

    private Step aggregateAllStatisticsStep() {
        return statisticsPartitionConfig.partitionStep();
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
                .<ContentStatistics, Settlement>chunk(100, transactionManager)
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
}
