package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.processor.DailyLogProcessor;
import com.github.garamflow.streamsettlement.batch.writer.DailyLogWriter;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class DailyLogAggregationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job dailyLogAggregationJob(Step dailyLogAggregationStep) {
        return new JobBuilder("dailyLogAggregationJob", jobRepository)
                .start(dailyLogAggregationStep)
                .build();
    }

    @Bean
    public Step dailyLogAggregationStep(
            JdbcPagingItemReader<DailyMemberViewLog> dailyLogReader,
            DailyLogProcessor dailyLogProcessor,
            DailyLogWriter dailyLogWriter) {
        return new StepBuilder("dailyLogAggregationStep", jobRepository)
                .<DailyMemberViewLog, ContentStatistics>chunk(500, transactionManager)
                .reader(dailyLogReader)
                .processor(dailyLogProcessor)
                .writer(dailyLogWriter)
                .build();
    }
}
