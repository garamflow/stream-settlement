package com.github.garamflow.streamsettlement.batch.settlement;

import com.github.garamflow.streamsettlement.batch.ContentStatisticsDto;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementStatus;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.exception.SettlementCalculationException;
import com.github.garamflow.streamsettlement.exception.SettlementProcessingException;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRateRepository;
import com.github.garamflow.streamsettlement.repository.statistics.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 정산 배치 처리를 위한 Spring Batch 설정 클래스입니다.
 * 일일 단위로 콘텐츠 통계 데이터를 기반으로 정산금액을 계산하고 저장합니다.
 *
 * <p>정산 처리 과정:</p>
 * <ul>
 *   <li>콘텐츠 통계 데이터 읽기 (Reader)</li>
 *   <li>정산금액 계산 (Processor)</li>
 *   <li>정산 데이터 저장 (Writer)</li>
 * </ul>
 */

@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class SettlementBatchConfig {
    private static final int CHUNK_SIZE = 1000;
    private static final int PAGE_SIZE = 10000;  // 한 번에 가져올 레코드 수

    private final SettlementRateRepository settlementRateRepository;
    private final StatisticsRepository statisticsRepository;
    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;
    private final JobRepository jobRepository;

    /**
     * [실행순서 1]
     * 일일 정산 처리를 위한 배치 Job을 생성합니다.
     * 스케줄러에 의해 매일 새벽 2시에 실행되며, dailySettlementStep을 시작합니다.
     *
     * @return 구성된 배치 Job
     * @throws Exception Job 생성 중 발생할 수 있는 예외
     */
    @Bean
    public Job dailySettlementJob() throws Exception {
        return new JobBuilder("dailySettlementJob", jobRepository)
                .start(dailySettlementStep())
                .build();
    }

    /**
     * [실행순서 2]
     * 일일 정산 처리를 위한 Step을 생성합니다.
     * Reader, Processor, Writer를 순차적으로 실행하며 chunk 단위로 트랜잭션을 관리합니다.
     *
     * @return 구성된 배치 Step
     * @throws Exception Step 생성 중 발생할 수 있는 예외
     */
    @Bean
    public Step dailySettlementStep() throws Exception {
        return new StepBuilder("dailySettlementStep", jobRepository)
                .<ContentStatisticsDto, Settlement>chunk(CHUNK_SIZE, transactionManager)
                .reader(contentStatisticsPagingReader())
                .processor(settlementProcessor())
                .writer(jdbcSettlementWriter())
                .build();
    }

    /**
     * [실행순서 3-1]
     * 콘텐츠 통계 데이터를 페이징 방식으로 읽어오는 Reader를 생성합니다.
     * 전일자의 일별 통계 데이터를 10,000건씩 조회합니다.
     * createQueryProvider()를 통해 생성된 쿼리로 데이터를 조회합니다.
     *
     * @return 구성된 JdbcPagingItemReader
     * @throws Exception Reader 생성 중 발생할 수 있는 예외
     */
    @Bean
    public JdbcPagingItemReader<ContentStatisticsDto> contentStatisticsPagingReader() throws Exception {
        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("date", LocalDate.now().minusDays(1));
        parameterValues.put("period", "DAILY");

        return new JdbcPagingItemReaderBuilder<ContentStatisticsDto>()
                .name("contentStatisticsReader")
                .dataSource(dataSource)
                .queryProvider(createQueryProvider())
                .parameterValues(parameterValues)
                .pageSize(PAGE_SIZE)
                .rowMapper((rs, rowNum) -> new ContentStatisticsDto(
                        rs.getLong("id"),
                        rs.getLong("content_post_id"),
                        rs.getObject("statistics_date", LocalDate.class),
                        rs.getLong("view_count"),
                        rs.getLong("watch_time"),
                        rs.getInt("duration")
                ))
                .build();
    }

    /**
     * [실행순서 3-2]
     * 페이징 쿼리 제공자를 생성합니다.
     * ContentStatistics와 ContentPost를 조인하여 정산에 필요한 데이터를 조회합니다.
     * Reader에서 사용되는 실제 SQL 쿼리를 구성합니다.
     *
     * @return 구성된 PagingQueryProvider
     * @throws Exception 쿼리 제공자 생성 중 발생할 수 있는 예외
     */
    private PagingQueryProvider createQueryProvider() throws Exception {
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);
        queryProvider.setSelectClause("SELECT cs.id, cs.content_post_id, cs.statistics_date, " +
                "cs.view_count, cs.watch_time, cp.duration");
        queryProvider.setFromClause("FROM content_statistics cs " +
                "JOIN content_post cp ON cs.content_post_id = cp.id");
        queryProvider.setWhereClause("WHERE cs.statistics_date = :date AND cs.period = :period");

        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        return queryProvider.getObject();
    }

    /**
     * [실행순서 4]
     * Reader에서 읽어온 데이터를 기반으로 정산금액을 계산하는 Processor를 생성합니다.
     * 다음 순서로 처리됩니다:
     * 1. 데이터 유효성 검증
     * 2. 컨텐츠 정산금액 계산: calculateAmountByType 호출
     * 3. 광고 수 계산: 영상 길이 5분당 1개
     * 4. 광고 정산금액 계산: calculateAmountByType 호출 * 광고 수
     * 5. 총 정산금액 계산: 컨텐츠 정산금액 + 광고 정산금액
     * 6. Settlement 엔티티 생성 및 반환
     *
     * @return 구성된 ItemProcessor
     * @throws SettlementProcessingException 정산 처리 중 오류 발생 시
     */
    @Bean
    public ItemProcessor<ContentStatisticsDto, Settlement> settlementProcessor() {
        return record -> {
            try {
                long totalViews = record.viewCount();
                LocalDateTime now = LocalDateTime.now();

                // 컨텐츠 정산금액 계산
                long contentAmount = calculateAmountByType(totalViews, SettlementType.CONTENT, now);

                // 광고 정산금액 계산
                int adCount = record.duration() / (5 * 60); // 5분당 1개
                long adAmount = calculateAmountByType(totalViews, SettlementType.ADVERTISEMENT, now) * adCount;

                // 총 정산금액 계산
                long totalAmount = contentAmount + adAmount;

                ContentStatistics contentStatistics = statisticsRepository.getReferenceById(record.id());

                return new Settlement.Builder()
                        .contentStatistics(contentStatistics)
                        .settlementDate(record.statisticsDate())
                        .dailyViews(record.viewCount())
                        .totalViews(record.viewCount())
                        .contentAmount(contentAmount)  // 계산된 값 사용
                        .adAmount(adAmount)           // 계산된 값 사용
                        .totalAmount(totalAmount)     // 계산된 값 사용
                        .status(SettlementStatus.CALCULATED)
                        .build();
            } catch (Exception e) {
                log.error("Error processing settlement for content statistics id: {}", record.id(), e);
                throw new SettlementProcessingException(
                        "Failed to process settlement for content statistics id: " + record.id(), e);
            }
        };
    }

    /**
     * [실행순서 5]
     * Processor에서 생성된 Settlement 엔티티를 데이터베이스에 저장하는 Writer를 생성합니다.
     * 1,000건(CHUNK_SIZE)씩 트랜잭션으로 처리되어 데이터베이스에 저장됩니다.
     * 중복 데이터 처리를 위해 UPSERT 방식을 사용합니다.
     * - 신규 데이터: INSERT 수행
     * - 기존 데이터: UPDATE 수행 (ON DUPLICATE KEY UPDATE)
     *
     * @return 구성된 JdbcBatchItemWriter
     */
    @Bean
    public JdbcBatchItemWriter<Settlement> jdbcSettlementWriter() {
        return new JdbcBatchItemWriterBuilder<Settlement>()
                .dataSource(dataSource)
                .sql("INSERT INTO settlement " +
                        "(content_statistics_id, settlement_date, daily_views, total_views, " +
                        "content_amount, ad_amount, total_amount, status, created_at) " +
                        "VALUES (:contentStatistics.id, :settlementDate, :dailyViews, :totalViews, " +
                        ":contentAmount, :adAmount, :totalAmount, :status, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "daily_views = :dailyViews, " +
                        "total_views = :totalViews, " +
                        "content_amount = :contentAmount, " +
                        "ad_amount = :adAmount, " +
                        "total_amount = :totalAmount, " +
                        "status = :status")
                .beanMapped()
                .build();
    }

    /**
     * [실행순서 4-1] (Processor 내부에서 호출)
     * 정산 유형별 금액을 계산합니다.
     * 다음 순서로 처리됩니다:
     * 1. 정산 유형(CONTENT/ADVERTISEMENT)에 따른 요율 정보 조회
     * 2. 조회수를 구간별로 분할
     * 3. 각 구간별 단가를 적용하여 정산금액 계산
     * 4. 전체 구간의 정산금액 합산하여 반환
     * 5. 전체 구간의 정산금액 합산하여 반환
     *
     * @param totalViews 총 조회수
     * @param type       정산 유형 (CONTENT 또는 ADVERTISEMENT)
     * @param dateTime   정산 기준 시점
     * @return 계산된 정산금액
     * @throws SettlementCalculationException 정산금액 계산 중 오버플로우 발생 시
     */
    private long calculateAmountByType(long totalViews, SettlementType type, LocalDateTime dateTime) {
        long amount = 0;
        long remainingViews = totalViews;

        List<SettlementRate> rates = settlementRateRepository.findBySettlementTypeOrderByMinViewsAsc(type)
                .stream()
                .filter(rate -> rate.isApplicable(dateTime))
                .toList();

        if (rates.isEmpty()) {
            log.warn("No settlement rates found for type: {} at datetime: {}", type, dateTime);
            return 0;
        }

        for (SettlementRate rate : rates) {
            if (remainingViews <= 0) break;

            long rangeStart = rate.getMinViews();
            long rangeEnd = rate.getMaxViews() != null ? rate.getMaxViews() : Long.MAX_VALUE;

            try {
                long viewsInRange = Math.min(remainingViews, rangeEnd - rangeStart + 1);
                amount = Math.addExact(amount, Math.multiplyExact(viewsInRange, rate.getRate().longValue()));
                remainingViews -= viewsInRange;
            } catch (ArithmeticException e) {
                log.error("Arithmetic overflow in settlement calculation", e);
                throw new SettlementCalculationException("Settlement amount calculation overflow", e);
            }
        }

        return amount;
    }
}
