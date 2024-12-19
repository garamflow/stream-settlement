package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndCumulativeSettlementDto;
import com.github.garamflow.streamsettlement.batch.incrementer.CustomJobParameterIncrementer;
import com.github.garamflow.streamsettlement.batch.listener.DailyLogAggregationStepListener;
import com.github.garamflow.streamsettlement.batch.partition.SettlementPartitioner;
import com.github.garamflow.streamsettlement.batch.partition.StatisticsPartitioner;
import com.github.garamflow.streamsettlement.batch.processor.SettlementItemProcessor;
import com.github.garamflow.streamsettlement.batch.processor.StatisticsItemProcessor;
import com.github.garamflow.streamsettlement.batch.reader.SettlementItemReader;
import com.github.garamflow.streamsettlement.batch.reader.StatisticsItemReader;
import com.github.garamflow.streamsettlement.batch.writer.SettlementItemWriter;
import com.github.garamflow.streamsettlement.batch.writer.StatisticsItemWriter;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.CompositeJobParametersValidator;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.Arrays;


/**
 * 배치 작업의 전체 구성을 정의하는 설정 클래스
 * - 일일 통계 및 정산 작업의 Job, Step, Partitioner 등을 구성
 * - 병렬 처리를 위한 ThreadPool 설정 포함
 */
@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private static final String JOB_NAME = "daily-statistics-settlement-job";
    private static final String STATISTICS_MASTER_STEP_NAME = "daily-statistics-master-step";
    private static final String STATISTICS_STEP_NAME = "daily-statistics-step";
    private static final String SETTLEMENT_MASTER_STEP_NAME = "daily-settlement-master-step";
    private static final String SETTLEMENT_STEP_NAME = "daily-settlement-step";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StatisticsPartitioner statisticsPartitioner;
    private final StatisticsItemReader statisticsItemReader;
    private final StatisticsItemProcessor statisticsItemProcessor;
    private final StatisticsItemWriter statisticsItemWriter;
    private final DailyLogAggregationStepListener dailyLogAggregationStepListener;
    private final SettlementPartitioner settlementPartitioner;
    private final SettlementItemReader settlementItemReader;
    private final SettlementItemProcessor settlementItemProcessor;
    private final SettlementItemWriter settlementItemWriter;
    private final BatchProperties batchProperties;

    /**
     * 메인 배치 Job 구성
     * 1. 통계 처리 Step (statisticsMasterStep)
     * 2. 정산 처리 Step (settlementMasterStep)
     * 순차적으로 실행
     */
    @Bean
    public Job dailyStatisticsAndSettlementJob(
            CustomJobParameterIncrementer incrementer,
            @Qualifier("statisticsMasterStep") Step statisticsMasterStep,
            @Qualifier("settlementMasterStep") Step settlementMasterStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(incrementer)
                .validator(validator())
                .start(statisticsMasterStep)
                .next(settlementMasterStep)
                .build();
    }

    /**
     * 통계 처리를 위한 파티션 Step 구성
     * - 데이터를 여러 파티션으로 나누어 병렬 처리
     */
    @Bean(name = "statisticsMasterStep")
    public Step dailyStatisticsPartitionMasterStep(
            @Qualifier("statisticsPartitionHandler") TaskExecutorPartitionHandler statisticsPartitionHandler) {
        return new StepBuilder(STATISTICS_MASTER_STEP_NAME, jobRepository)
                .partitioner(STATISTICS_STEP_NAME, statisticsPartitioner)
                .partitionHandler(statisticsPartitionHandler)
                .build();
    }

    /**
     * 정산 처리를 위한 마스터 Step 구성
     * - 정산 데이터를 파티션 단위로 분할하여 병렬 처리
     */
    @Bean(name = "settlementMasterStep")
    public Step dailySettlementPartitionMasterStep(
            @Qualifier("settlementPartitionHandler") TaskExecutorPartitionHandler settlementPartitionHandler) {
        return new StepBuilder(SETTLEMENT_MASTER_STEP_NAME, jobRepository)
                .partitioner(SETTLEMENT_STEP_NAME, settlementPartitioner)
                .partitionHandler(settlementPartitionHandler)
                .build();
    }

    /**
     * 통계 처리를 위한 파티션 핸들러 구성
     * - 파티션된 작업을 스레드풀을 통해 병렬 실행
     */
    @Bean(name = "statisticsPartitionHandler")
    public TaskExecutorPartitionHandler dailyStatisticsPartitionHandler(
            @Qualifier("statisticsStep") Step statisticsStep) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(statisticsStep);
        partitionHandler.setTaskExecutor(executor());
        partitionHandler.setGridSize(batchProperties.getGridSize());
        return partitionHandler;
    }

    /**
     * 정산 처리를 위한 파티션 핸들러 구성
     * - 파티션된 작업을 스레드풀을 통해 병렬 실행
     */
    @Bean(name = "settlementPartitionHandler")
    public TaskExecutorPartitionHandler dailySettlementPartitionHandler(
            @Qualifier("settlementStep") Step settlementStep) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(settlementStep);
        partitionHandler.setTaskExecutor(executor());
        partitionHandler.setGridSize(batchProperties.getGridSize());
        return partitionHandler;
    }

    /**
     * 배치 작업 실행을 위한 스레드풀 구성
     * - 코어 풀 사이즈, 최대 풀 사이즈, 큐 용량 등 설정
     */
    @Bean
    public TaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProperties.getPool().getCoreSize());
        executor.setMaxPoolSize(batchProperties.getPool().getMaxSize());
        executor.setQueueCapacity(batchProperties.getPool().getQueueCapacity());
        executor.setThreadNamePrefix(batchProperties.getPool().getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * 통계 처리를 위한 파티션 Step 구성
     * - 데이터를 여러 파티션으로 나누어 병렬 처리
     */
    @Bean(name = "statisticsStep")
    public Step dailyStatisticsStep() {
        return new StepBuilder(STATISTICS_STEP_NAME, jobRepository)
                .<CumulativeStatisticsDto, ContentStatistics>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(statisticsItemReader)
                .processor(statisticsItemProcessor)
                .writer(statisticsItemWriter)
                .listener(dailyLogAggregationStepListener)
                .faultTolerant()
                .retryLimit(3)
                .retry(CannotAcquireLockException.class)
                .backOffPolicy(new ExponentialBackOffPolicy())
                .build();
    }

    /**
     * 정산 처리를 위한 워커 Step 구성
     * - 청크 단위로 데이터 처리
     * - 재시도 및 오류 처리 정책 포함
     */
    @Bean(name = "settlementStep")
    public Step dailySettlementStep() {
        return new StepBuilder(SETTLEMENT_STEP_NAME, jobRepository)
                .<StatisticsAndCumulativeSettlementDto, Settlement>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(settlementItemReader)
                .processor(settlementItemProcessor)
                .writer(settlementItemWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(CannotAcquireLockException.class)
                .backOffPolicy(new ExponentialBackOffPolicy())
                .build();
    }

    /**
     * Job 파라미터 유효성 검증기 구성
     * - targetDate 파라미터 필수 체크
     * - 날짜 형식 유효성 검증
     */
    private JobParametersValidator validator() {
        DefaultJobParametersValidator defaultValidator = new DefaultJobParametersValidator();
        defaultValidator.setRequiredKeys(new String[]{"targetDate"});

        CompositeJobParametersValidator compositeValidator = new CompositeJobParametersValidator();
        compositeValidator.setValidators(Arrays.asList(
                defaultValidator,
                parameters -> {
                    String targetDate = parameters != null ? parameters.getString("targetDate") : null;
                    if (targetDate == null || targetDate.isEmpty()) {
                        throw new IllegalArgumentException("'targetDate' is required and must not be empty.");
                    }
                    LocalDate.parse(targetDate);
                }
        ));

        return compositeValidator;
    }
}
