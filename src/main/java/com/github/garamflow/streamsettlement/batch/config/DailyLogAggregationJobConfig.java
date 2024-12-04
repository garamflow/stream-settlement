package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.partition.DailyLogPartitioner;
import com.github.garamflow.streamsettlement.batch.processor.DailyLogProcessor;
import com.github.garamflow.streamsettlement.batch.writer.DailyLogWriter;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DailyLogAggregationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DailyLogPartitioner partitioner;

    @Value("${batch.partition.size:4}")  // 기본값 4
    private int partitionSize;

    @Bean
    public Job dailyLogAggregationJob(Step masterStep) {
        return new JobBuilder("dailyLogAggregationJob", jobRepository)
                .validator(validator()) // 추가: Job 파라미터 검증기 설정
                .start(masterStep)
                .build();
    }

    @Bean
    public Step masterStep(Step workerStep) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .step(workerStep)
                .gridSize(partitionSize)
                .taskExecutor(taskExecutor())
                .build();
    }

    @Bean
    public Step workerStep(
            JdbcPagingItemReader<DailyMemberViewLog> reader,
            DailyLogProcessor dailyLogProcessor,
            DailyLogWriter dailyLogWriter) {
        return new StepBuilder("workerStep", jobRepository)
                .<DailyMemberViewLog, List<ContentStatistics>>chunk(50, transactionManager)
                .reader(reader)
                .processor(dailyLogProcessor)
                .writer(dailyLogWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(CannotAcquireLockException.class)
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("partition-thread-");
        executor.setQueueCapacity(25);
        executor.initialize();
        return executor;
    }

    private JobParametersValidator validator() {
        DefaultJobParametersValidator defaultValidator = new DefaultJobParametersValidator();
        defaultValidator.setRequiredKeys(new String[]{"targetDate"}); // 필수 파라미터 설정

        CompositeJobParametersValidator compositeValidator = new CompositeJobParametersValidator();
        compositeValidator.setValidators(Arrays.asList(
                defaultValidator,
                parameters -> {
                    // targetDate 파라미터를 문자열로 가져오기
                    String targetDate = parameters.getString("targetDate");
                    if (targetDate == null || targetDate.isEmpty()) {
                        throw new IllegalArgumentException("'targetDate' is required and must not be empty.");
                    }

                    try {
                        LocalDate.parse(targetDate); // targetDate 형식 검증
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Invalid 'targetDate' format. Expected format is yyyy-MM-dd.", e);
                    }
                }
        ));

        return compositeValidator;
    }
}
