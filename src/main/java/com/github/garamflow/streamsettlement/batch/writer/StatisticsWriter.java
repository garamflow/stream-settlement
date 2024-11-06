package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.exception.StatisticsWriteException;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * 생성된 통계 데이터를 저장하는 Writer 클래스 (전체 실행 순서 중 3.4단계)
 *
 * <p>여러 기간의 통계 데이터를 배치로 저장합니다.</p>
 *
 * <p><b>실행 순서:</b></p>
 * <ol>
 *     <li>initialize() - PostConstruct 로 초기화
 *         <ul>
 *             <li>JdbcBatchItemWriter 생성</li>
 *             <li>UPSERT SQL 준비</li>
 *         </ul>
 *     </li>
 *     <li>write() - 청크 단위로 실행
 *         <ul>
 *             <li>List<ContentStatistics> 평탄화</li>
 *             <li>배치 Insert/Update 실행</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p><b>데이터 처리:</b></p>
 * <ul>
 *     <li>입력: List<List<ContentStatistics>></li>
 *     <li>저장: content_statistics 테이블</li>
 *     <li>처리: UPSERT(Insert or Update) 방식</li>
 * </ul>
 *
 * <p><b>Update 정책:</b></p>
 * <ul>
 *     <li>조회수: 누적</li>
 *     <li>시청시간: 누적</li>
 *     <li>누적조회수: 최신값으로 갱신</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsWriter implements ItemWriter<List<ContentStatistics>> {

    private final DataSource dataSource;
    private JdbcBatchItemWriter<ContentStatistics> delegate;

    /**
     * Writer 초기화를 수행합니다. (실행 순서 1단계)
     *
     * <p>애플리케이션 시작 시 @PostConstruct 로 자동 실행됩니다.</p>
     *
     * <p><b>초기화 과정:</b></p>
     * <ol>
     *     <li>createBatchWriter()를 통해 JdbcBatchItemWriter 생성</li>
     *     <li>생성된 writer 의 afterPropertiesSet() 호출하여 초기화 완료</li>
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
     * 통계 데이터를 일괄 저장합니다. (실행 순서 2단계)
     *
     * <p>Spring Batch 의 청크 단위 처리에서 청크 크기만큼의 데이터가 모이면 호출됩니다.</p>
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>청크 내의 중첩 리스트를 단일 리스트로 평탄화
     *         <ul>
     *             <li>입력: List&lt;List&lt;ContentStatistics&gt;&gt;</li>
     *             <li>변환: List&lt;ContentStatistics&gt;</li>
     *         </ul>
     *     </li>
     *     <li>UPSERT SQL 실행
     *         <ul>
     *             <li>기존 데이터 없음: INSERT 수행</li>
     *             <li>기존 데이터 있음: UPDATE 수행 (누적 값 계산)</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * @param chunk 저장할 통계 데이터 청크 (중첩 리스트 형태, null 이 아님)
     * @throws StatisticsWriteException 데이터 저장 중 오류 발생 시
     */
    @Override
    public void write(@NonNull Chunk<? extends List<ContentStatistics>> chunk) {
        try {
            List<ContentStatistics> flattenedStatistics = chunk.getItems().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            log.debug("Writing {} statistics records", flattenedStatistics.size());

            // UPSERT 수행
            delegate.write(new Chunk<>(flattenedStatistics));

            log.debug("Successfully wrote {} statistics records", flattenedStatistics.size());

        } catch (Exception e) {
            log.error("Failed to write statistics. Chunk size: {}", chunk.size(), e);
            throw new StatisticsWriteException("Failed to write statistics", e);
        }
    }

    /**
     * JdbcBatchItemWriter 를 생성하고 설정합니다. (initialize 내부에서 호출)
     *
     * <p><b>writer 구성:</b></p>
     * <ul>
     *     <li>beanMapped() 설정으로 객체-컬럼 자동 매핑</li>
     *     <li>UPSERT 방식의 SQL 쿼리 사용</li>
     * </ul>
     *
     * <p><b>SQL 동작 방식:</b></p>
     * <pre>
     * 1. 신규 데이터 INSERT 시도
     * 2. 키 충돌 발생 시:
     *    - view_count: 기존 값 + 신규 값
     *    - total_watch_time: 기존 값 + 신규 값
     *    - accumulated_views: 신규 값으로 덮어쓰기
     * </pre>
     *
     * @return 구성된 JdbcBatchItemWriter 객체
     * @throws BatchConfigurationException writer 생성 실패 시 발생
     */
    private JdbcBatchItemWriter<ContentStatistics> createBatchWriter() {
        try {
            return new JdbcBatchItemWriterBuilder<ContentStatistics>()
                    .dataSource(dataSource)
                    .sql("""
                            INSERT INTO content_statistics
                                (content_id, view_count, total_watch_time,
                                 period, statistics_date, accumulated_views)
                            VALUES
                                (:contentId, :viewCount, :totalWatchTime,
                                 :period, :statisticsDate, :accumulatedViews)
                            ON DUPLICATE KEY UPDATE
                                view_count = view_count + VALUES(view_count),
                                total_watch_time = total_watch_time + VALUES(total_watch_time),
                                accumulated_views = VALUES(accumulated_views)
                            """)
                    .beanMapped()
                    .build();

        } catch (Exception e) {
            log.error("Failed to create batch writer", e);
            throw new BatchConfigurationException("Failed to create statistics writer", e);
        }
    }
}
