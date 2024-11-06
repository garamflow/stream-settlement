package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.BatchConfigurationException;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Map;

/**
 * 정산 처리를 위한 통계 데이터를 읽어오는 Reader 클래스 (전체 실행 순서 중 4.2단계)
 *
 * <p>전일 생성된 통계 데이터를 데이터베이스에서 조회하여 정산 처리를 위한 데이터를 제공합니다.</p>
 *
 * <p><b>실행 순서:</b></p>
 * <ol>
 *     <li>initialize() - 초기 설정</li>
 *     <li>read() - 반복 실행</li>
 * </ol>
 *
 * <p><b>처리 데이터:</b></p>
 * <ul>
 *     <li>입력: content_statistics 테이블과 content_post 테이블 조인</li>
 *     <li>출력: ContentStatistics 엔티티</li>
 * </ul>
 *
 * <p><b>조회 조건:</b></p>
 * <ul>
 *     <li>전일 생성된 통계 데이터</li>
 *     <li>content_id 기준 오름차순 정렬</li>
 *     <li>100건 단위 페이징 처리</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsReader implements ItemReader<ContentStatistics> {

    private final DataSource dataSource;
    private JdbcPagingItemReader<ContentStatistics> delegate;

    /**
     * Reader 초기화를 수행합니다. (실행 순서 1단계)
     *
     * <p>애플리케이션 시작 시 @PostConstruct 로 자동 실행됩니다.</p>
     *
     * <p><b>초기화 과정:</b></p>
     * <ol>
     *     <li>createPagingReader()를 통해 JdbcPagingItemReader 생성</li>
     *     <li>생성된 reader 의 afterPropertiesSet() 호출</li>
     * </ol>
     *
     * @throws BatchConfigurationException Reader 초기화 실패 시 발생
     */
    @PostConstruct
    public void initialize() {
        this.delegate = createPagingReader();
        try {
            this.delegate.afterPropertiesSet();
        } catch (Exception e) {
            throw new BatchConfigurationException("Failed to initialize reader", e);
        }
    }

    /**
     * 통계 데이터를 한 건씩 읽어옵니다. (실행 순서 2단계)
     *
     * <p>Spring Batch 의 청크 단위 처리에서 반복적으로 호출됩니다.</p>
     *
     * <p><b>동작 방식:</b></p>
     * <ul>
     *     <li>delegate reader 를 통해 실제 데이터 읽기 수행</li>
     *     <li>더 이상 읽을 데이터가 없으면 null 반환</li>
     *     <li>페이징 처리는 내부적으로 자동 수행</li>
     * </ul>
     *
     * @return 읽어온 ContentStatistics 객체, 더 이상 데이터가 없으면 null
     * @throws IllegalStateException Reader 가 초기화되지 않은 경우
     * @throws Exception 데이터 읽기 중 발생하는 예외
     */
    @Override
    public ContentStatistics read() throws Exception {
        if (delegate == null) {
            throw new IllegalStateException("Reader not initialized");
        }
        return delegate.read();
    }

    /**
     * JdbcPagingItemReader 를 생성하고 설정합니다. (initialize 내부에서 호출)
     *
     * <p><b>설정 내용:</b></p>
     * <ul>
     *     <li>reader 이름: "statisticsReader"</li>
     *     <li>페이지 크기: 100개</li>
     *     <li>정렬 기준: content_id 오름차순</li>
     * </ul>
     *
     * <p><b>SQL 구성:</b></p>
     * <pre>
     * SELECT cs.content_id, cs.view_count, cs.total_watch_time,
     *        cs.period, cs.statistics_date, cp.video_length
     * FROM content_statistics cs
     * JOIN content_post cp ON cs.content_id = cp.id
     * WHERE cs.statistics_date = :targetDate
     * ORDER BY content_id ASC
     * </pre>
     *
     * <p><b>참고사항:</b></p>
     * <ul>
     *     <li>content_post 와의 조인을 통해 동영상 길이 정보 조회</li>
     *     <li>BeanPropertyRowMapper 를 사용하여 자동 매핑</li>
     * </ul>
     *
     * @return 구성된 JdbcPagingItemReader 객체
     * @throws BatchConfigurationException Reader 생성 실패 시 발생
     */
    private JdbcPagingItemReader<ContentStatistics> createPagingReader() {
        try {
            LocalDate targetDate = LocalDate.now().minusDays(1);

            return new JdbcPagingItemReaderBuilder<ContentStatistics>()
                    .name("statisticsReader")
                    .dataSource(dataSource)
                    .pageSize(100)
                    .selectClause("""
                            SELECT cs.content_id, cs.view_count, cs.total_watch_time,
                                   cs.period, cs.statistics_date, cp.video_length
                            """)
                    .fromClause("""
                            FROM content_statistics cs
                            JOIN content_post cp ON cs.content_id = cp.id
                            """)
                    .whereClause("WHERE cs.statistics_date = :targetDate")
                    .parameterValues(Map.of("targetDate", targetDate))
                    .sortKeys(Map.of("content_id", Order.ASCENDING))
                    .rowMapper(new BeanPropertyRowMapper<>(ContentStatistics.class))
                    .build();

        } catch (Exception e) {
            log.error("Failed to create paging reader", e);
            throw new BatchConfigurationException("Failed to create statistics reader", e);
        }
    }
}
