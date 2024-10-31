package com.github.garamflow.streamsettlement.batch.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyUserViewLog;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/**
 * 콘텐츠 통계 집계를 위한 Spring Batch 설정 클래스입니다.
 * 각 콘텐츠의 일간, 주간, 월간, 연간 단위로 조회수와 시청 시간을 집계합니다.
 */
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class StatisticsBatchConfig {

    private final DataSource dataSource;
    private final PlatformTransactionManager platformTransactionManager;
    private final JobRepository jobRepository;

    /**
     * 콘텐츠 통계 집계 작업을 위한 Batch Job을 생성합니다.
     * 일간, 주간, 월간, 연간 통계 집계를 각 Step에서 수행합니다.
     *
     * @return 구성된 통계 집계 Job 객체
     */
    @Bean
    public Job statisticsAggregationJob() {
        return new JobBuilder("statisticsAggregationJob", jobRepository)
                .start(aggregateStatisticsStep(StatisticsPeriod.DAILY, "daily"))
                .next(aggregateStatisticsStep(StatisticsPeriod.WEEKLY, "weekly"))
                .next(aggregateStatisticsStep(StatisticsPeriod.MONTHLY, "monthly"))
                .next(aggregateStatisticsStep(StatisticsPeriod.YEARLY, "yearly"))
                .build();
    }

    /**
     * 특정 기간의 콘텐츠 조회 로그를 통계 데이터로 변환하고 집계하는 Batch Step을 생성합니다.
     *
     * @param period   집계 기간 (일간, 주간, 월간, 연간)
     * @param stepName Step의 식별자로 사용될 이름
     * @return 구성된 Step 객체
     */
    private Step aggregateStatisticsStep(StatisticsPeriod period, String stepName) {
        return new StepBuilder("aggregate" + stepName + "StatisticsStep", jobRepository)
                .<DailyUserViewLog, ContentStatistics>chunk(100, platformTransactionManager)
                .reader(createLogReader(stepName + "Reader", period))
                .processor(createProcessor(period))
                .writer(statisticsWriter())
                .faultTolerant()
                .retry(Exception.class)
                .retryLimit(3)
                .build();
    }

    /**
     * 지정된 기간에 해당하는 사용자 조회 로그 데이터를 읽어오는 ItemReader를 생성합니다.
     * 해당 기간의 로그 데이터를 페이징 방식으로 조회합니다.
     *
     * @param stepName Reader의 식별자로 사용될 이름
     * @param period   집계 기간
     * @return 구성된 ItemReader 객체
     */
    private ItemReader<DailyUserViewLog> createLogReader(String stepName, StatisticsPeriod period) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = getStartDate(period, endDate);

        return new JdbcPagingItemReaderBuilder<DailyUserViewLog>()
                .name(stepName)
                .dataSource(dataSource)
                .pageSize(10)
                .selectClause("SELECT id, user_id, content_post_id, last_viewed_position, log_date, status")
                .fromClause("FROM daily_user_view_log")
                .whereClause("WHERE log_date BETWEEN :startDate AND :endDate")
                .parameterValues(Map.of(
                        "startDate", startDate,
                        "endDate", endDate
                ))
                .sortKeys(Collections.singletonMap("id", Order.ASCENDING))
                .rowMapper(new BeanPropertyRowMapper<>(DailyUserViewLog.class))
                .build();
    }

    /**
     * 각 집계 기간에 따른 통계 데이터를 생성하는 Processor를 정의합니다.
     *
     * @param period 통계 집계 기간
     * @return DailyUserViewLog 데이터를 ContentStatistics로 변환하는 ItemProcessor 객체
     */
    private ItemProcessor<DailyUserViewLog, ContentStatistics> createProcessor(StatisticsPeriod period) {
        return item -> {
            LocalDate statisticsDate = switch (period) {
                case DAILY -> item.getLogDate();
                case WEEKLY -> item.getLogDate().with(previousOrSame(DayOfWeek.MONDAY));
                case MONTHLY -> item.getLogDate().withDayOfMonth(1);
                case YEARLY -> item.getLogDate().withDayOfYear(1);
            };

            return new ContentStatistics.Builder()
                    .contentPost(item.getContentPost())
                    .statisticsDate(statisticsDate)
                    .period(period)
                    .viewCount(1L)  // 각 로그는 1회의 조회수
                    .watchTime((long) item.getLastViewedPosition())  // 시청 시간
                    .build();
        };
    }

    /**
     * 변환된 통계 데이터를 데이터베이스에 저장하는 ItemWriter를 생성합니다.
     *
     * @return 구성된 ItemWriter 객체
     */
    @Bean
    public JdbcBatchItemWriter<ContentStatistics> statisticsWriter() {
        return new JdbcBatchItemWriterBuilder<ContentStatistics>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO content_statistics 
                        (content_id, view_count, total_watch_time, period, statistics_date) 
                        VALUES (:contentId, :viewCount, :totalWatchTime, :period, :statisticsDate)
                        """)
                .beanMapped()
                .build();
    }

    /**
     * 주어진 통계 집계 기간에 해당하는 시작 날짜를 계산합니다.
     *
     * @param period  통계 집계 기간
     * @param endDate 통계 집계 종료 날짜
     * @return 계산된 시작 날짜
     */
    private LocalDate getStartDate(StatisticsPeriod period, LocalDate endDate) {
        return switch (period) {
            case DAILY -> endDate;
            case WEEKLY -> endDate.minusDays(6); // 일주일간 데이터
            case MONTHLY -> endDate.withDayOfMonth(1); // 이번 달 1일부터
            case YEARLY -> endDate.withDayOfYear(1); // 올해 1월 1일부터
        };
    }
}

