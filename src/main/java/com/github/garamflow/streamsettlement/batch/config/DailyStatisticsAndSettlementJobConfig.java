package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndSettlementDto;
import com.github.garamflow.streamsettlement.batch.listener.DailyLogAggregationStepListener;
import com.github.garamflow.streamsettlement.batch.partition.DailyLogPartitioner;
import com.github.garamflow.streamsettlement.batch.partition.SettlementPartitioner;
import com.github.garamflow.streamsettlement.batch.processor.DailyLogProcessor;
import com.github.garamflow.streamsettlement.batch.processor.SettlementItemProcessor;
import com.github.garamflow.streamsettlement.batch.reader.SettlementItemReader;
import com.github.garamflow.streamsettlement.batch.writer.DailyLogWriter;
import com.github.garamflow.streamsettlement.batch.writer.SettlementItemWriter;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.CompositeJobParametersValidator;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DailyStatisticsAndSettlementJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DailyLogPartitioner statisticsPartitioner;
    private final SettlementPartitioner settlementPartitioner;
    private final BatchProperties batchProperties;

    private static final String JOB_NAME = "dailyStatisticsAndSettlementJob";
    private static final String STATISTICS_MASTER_STEP_NAME = "statisticsMasterStep";
    private static final String STATISTICS_WORKER_STEP_NAME = "statisticsWorkerStep";
    private static final String SETTLEMENT_MASTER_STEP_NAME = "settlementMasterStep";
    private static final String SETTLEMENT_WORKER_STEP_NAME = "settlementWorkerStep";

    @Bean
    public Job dailyStatisticsAndSettlementJob(
            @Qualifier("statisticsMasterStep") Step statisticsMasterStep,
            @Qualifier("settlementMasterStep") Step settlementMasterStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(validator())
                .start(statisticsMasterStep)
                .next(settlementMasterStep)
                .build();
    }

    @Bean
    @Qualifier("statisticsMasterStep")
    public Step statisticsMasterStep(
            @Qualifier("statisticsWorkerStep") Step statisticsWorkerStep) {
        return new StepBuilder(STATISTICS_MASTER_STEP_NAME, jobRepository)
                .partitioner(STATISTICS_WORKER_STEP_NAME, statisticsPartitioner)
                .step(statisticsWorkerStep)
                .gridSize(batchProperties.getMaxGridSize())
                .taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    @Qualifier("settlementMasterStep")
    public Step settlementMasterStep(
            @Qualifier("settlementWorkerStep") Step settlementWorkerStep) {
        return new StepBuilder(SETTLEMENT_MASTER_STEP_NAME, jobRepository)
                .partitioner(SETTLEMENT_WORKER_STEP_NAME, settlementPartitioner)
                .step(settlementWorkerStep)
                .gridSize(batchProperties.getMaxGridSize())
                .taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    @Qualifier("statisticsWorkerStep")
    public Step statisticsWorkerStep(
            JdbcPagingItemReader<MemberContentWatchLog> reader,
            DailyLogProcessor processor,
            DailyLogWriter writer,
            DailyLogAggregationStepListener stepListener) {
        return new StepBuilder(STATISTICS_WORKER_STEP_NAME, jobRepository)
                .<MemberContentWatchLog, List<ContentStatistics>>chunk(
                        batchProperties.getChunkSize(), transactionManager)
                .listener(stepListener)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(CannotAcquireLockException.class)
                .backOffPolicy(new ExponentialBackOffPolicy())
                .build();
    }

    @Bean
    @Qualifier("settlementWorkerStep")
    public Step settlementWorkerStep(
            SettlementItemReader reader,
            SettlementItemProcessor processor,
            SettlementItemWriter writer) {
        return new StepBuilder(SETTLEMENT_WORKER_STEP_NAME, jobRepository)
                .<StatisticsAndSettlementDto, Settlement>chunk(
                        batchProperties.getChunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retryLimit(3)
                .retry(CannotAcquireLockException.class)
                .backOffPolicy(new ExponentialBackOffPolicy())
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProperties.getPool().getCoreSize());
        executor.setMaxPoolSize(batchProperties.getPool().getMaxSize());
        executor.setThreadNamePrefix("daily-log-thread-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    private JobParametersValidator validator() {
        DefaultJobParametersValidator defaultValidator = new DefaultJobParametersValidator();
        defaultValidator.setRequiredKeys(new String[]{"targetDate"});

        CompositeJobParametersValidator compositeValidator = new CompositeJobParametersValidator();
        compositeValidator.setValidators(Arrays.asList(
                defaultValidator,
                parameters -> {
                    // targetDate 파라미터를 문자열로 가져오기
                    String targetDate = parameters != null ? parameters.getString("targetDate") : null;
                    if (targetDate == null || targetDate.isEmpty()) {
                        throw new IllegalArgumentException("'targetDate' is required and must not be empty.");
                    }

                    try {
                        LocalDate.parse(targetDate);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Invalid 'targetDate' format. Expected format is yyyy-MM-dd.", e);
                    }
                }
        ));

        return compositeValidator;
    }
}
