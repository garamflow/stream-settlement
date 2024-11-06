package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.exception.SettlementWriteException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.BatchConfigurationException;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 계산된 정산 데이터를 데이터베이스에 저장하는 Writer 클래스 (전체 실행 순서 중 4.4단계)
 *
 * <p>정산 처리의 마지막 단계로, 계산된 정산 금액과 관련 정보를 저장합니다.</p>
 *
 * <p><b>실행 순서:</b></p>
 * <ol>
 *     <li>initialize() - 초기 설정</li>
 *     <li>write() - 청크 단위로 데이터 저장</li>
 * </ol>
 *
 * <p><b>처리 데이터:</b></p>
 * <ul>
 *     <li>입력: Settlement 엔티티</li>
 *     <li>저장: settlement 테이블</li>
 * </ul>
 *
 * <p><b>저장 방식:</b></p>
 * <ul>
 *     <li>UPSERT(INSERT/UPDATE) 방식 사용</li>
 *     <li>동일 키 존재 시 모든 값 갱신</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementWriter implements ItemWriter<Settlement> {

    private final DataSource dataSource;
    private JdbcBatchItemWriter<Settlement> delegate;

    /**
     * Writer 초기화를 수행합니다. (실행 순서 1단계)
     *
     * <p>애플리케이션 시작 시 @PostConstruct 로 자동 실행됩니다.</p>
     *
     * <p><b>초기화 과정:</b></p>
     * <ol>
     *     <li>createBatchWriter()를 통해 JdbcBatchItemWriter 생성</li>
     *     <li>생성된 writer 의 afterPropertiesSet() 호출</li>
     * </ol>
     *
     * @throws BatchConfigurationException Writer 초기화 실패 시 발생
     */
    @PostConstruct
    public void initialize() {
        this.delegate = createBatchWriter();
        try {
            this.delegate.afterPropertiesSet();
        } catch (Exception e) {
            throw new BatchConfigurationException("Failed to initialize writer", e);
        }
    }

    /**
     * 정산 데이터를 일괄 저장합니다. (실행 순서 2단계)
     *
     * <p>Spring Batch 의 청크 단위 처리에서 청크 크기만큼의 데이터가 모이면 호출됩니다.</p>
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>로그 기록 - 저장 시작</li>
     *     <li>delegate writer 를 통한 배치 저장 실행</li>
     *     <li>로그 기록 - 저장 완료</li>
     * </ol>
     *
     * @param chunk 저장할 정산 데이터 청크
     * @throws SettlementWriteException 데이터 저장 중 오류 발생 시
     */
    @Override
    public void write(@NonNull Chunk<? extends Settlement> chunk) {
        try {
            log.debug("Writing {} settlement records", chunk.size());
            delegate.write(chunk);
            log.debug("Successfully wrote {} settlement records", chunk.size());
        } catch (Exception e) {
            log.error("Failed to write settlements. Size: {}", chunk.size(), e);
            throw new SettlementWriteException("Failed to write settlements", e);
        }
    }

    /**
     * JdbcBatchItemWriter 를 생성하고 설정합니다. (initialize 내부에서 호출)
     *
     * <p><b>Writer 구성:</b></p>
     * <ul>
     *     <li>beanMapped() 설정으로 객체-컬럼 자동 매핑</li>
     *     <li>UPSERT 방식의 SQL 쿼리 사용</li>
     * </ul>
     *
     * <p><b>SQL 동작 방식:</b></p>
     * <pre>
     * 1. content_statistics_id를 기준으로 데이터 존재 여부 확인
     * 2. 데이터가 없는 경우: 새로운 정산 데이터 INSERT
     * 3. 데이터가 있는 경우: 다음 필드들을 UPDATE
     *    - daily_views
     *    - total_views
     *    - content_amount
     *    - ad_amount
     *    - total_amount
     *    - status
     * </pre>
     *
     * @return 구성된 JdbcBatchItemWriter 객체
     */
    private JdbcBatchItemWriter<Settlement> createBatchWriter() {
        return new JdbcBatchItemWriterBuilder<Settlement>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO settlement
                            (content_statistics_id, settlement_date, daily_views,
                             content_amount, ad_amount, total_amount, status,
                             total_views)
                        VALUES
                            (:contentStatistics.id, :settlementDate, :dailyViews,
                             :contentAmount, :adAmount, :totalAmount, :status,
                             :totalViews)
                        ON DUPLICATE KEY UPDATE
                            daily_views = :dailyViews,
                            total_views = :totalViews,
                            content_amount = :contentAmount,
                            ad_amount = :adAmount,
                            total_amount = :totalAmount,
                            status = :status
                        """)
                .beanMapped()
                .build();
    }
}
