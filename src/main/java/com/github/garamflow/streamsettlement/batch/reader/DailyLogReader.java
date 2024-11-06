package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
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
import java.util.Collections;
import java.util.Map;

/**
 * 일별 시청 로그를 읽어오는 Reader 클래스 (전체 실행 순서 중 3.2단계)
 *
 * <p>전일의 시청 로그 데이터를 데이터베이스에서 조회하여 처리합니다.</p>
 *
 * <p><b>실행 순서:</b></p>
 * <ol>
 *     <li>initialize() - PostConstruct 로 초기화
 *         <ul>
 *             <li>JdbcPagingItemReader 생성</li>
 *             <li>페이지 사이즈 설정</li>
 *         </ul>
 *     </li>
 *     <li>read() - 청크 단위로 반복 실행
 *         <ul>
 *             <li>delegate 를 통해 실제 데이터 읽기</li>
 *             <li>더 이상 데이터가 없을 때까지 반복</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p><b>처리 데이터:</b></p>
 * <ul>
 *     <li>입력: daily_user_view_log 테이블</li>
 *     <li>출력: DailyMemberViewLog 엔티티</li>
 * </ul>
 *
 * <p><b>조회 조건:</b></p>
 * <ul>
 *     <li>전일 데이터만 조회 (log_date = CURRENT_DATE - 1)</li>
 *     <li>ID 기준 오름차순 정렬</li>
 *     <li>100건 단위 페이징 처리</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogReader implements ItemReader<DailyMemberViewLog> {

    private static final Integer PAGE_SIZE = 100;

    private final DataSource dataSource;
    private JdbcPagingItemReader<DailyMemberViewLog> delegate;

    /**
     * Reader 초기화를 수행합니다. (실행 순서 1단계)
     *
     * <p>애플리케이션 시작 시 @PostConstruct 로 자동 실행됩니다.</p>
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>createPagingReader()를 통해 JdbcPagingItemReader 생성</li>
     *     <li>생성된 reader 의 afterPropertiesSet() 호출하여 초기화 완료</li>
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
     * 시청 로그 데이터를 한 건씩 읽어옵니다. (실행 순서 2단계)
     *
     * <p>Spring Batch 의 청크 단위 처리에서 반복적으로 호출됩니다.</p>
     *
     * <p><b>동작 방식:</b></p>
     * <ul>
     *     <li>delegate reader 를 통해 실제 데이터 읽기 수행</li>
     *     <li>더 이상 읽을 데이터가 없으면 null 반환</li>
     *     <li>페이징 처리는 내부적으로 자동으로 수행됨</li>
     * </ul>
     *
     * @return 읽어온 DailyMemberViewLog 객체, 더 이상 데이터가 없으면 null
     * @throws IllegalStateException Reader 가 초기화되지 않은 경우
     * @throws Exception 데이터 읽기 중 발생하는 예외
     */
    @Override
    public DailyMemberViewLog read() throws Exception {
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
     *     <li>reader 이름: "dailyLogReader"</li>
     *     <li>페이지 크기: {@value PAGE_SIZE}개</li>
     *     <li>조회 대상: 전일자 시청 로그</li>
     *     <li>정렬 기준: ID 오름차순</li>
     * </ul>
     *
     * <p><b>SQL 구성:</b></p>
     * <pre>
     * SELECT id, user_id, content_post_id, last_viewed_position, log_date, status
     * FROM daily_user_view_log
     * WHERE log_date = :targetDate
     * ORDER BY id ASC
     * </pre>
     *
     * @return 구성된 JdbcPagingItemReader 객체
     * @throws BatchConfigurationException Reader 생성 실패 시 발생
     */
    private JdbcPagingItemReader<DailyMemberViewLog> createPagingReader() {
        try {
            LocalDate targetDate = LocalDate.now().minusDays(1); // 전일 데이터 처리

            return new JdbcPagingItemReaderBuilder<DailyMemberViewLog>()
                    .name("dailyLogReader")
                    .dataSource(dataSource)
                    .pageSize(PAGE_SIZE)
                    .selectClause("SELECT id, user_id, content_post_id, last_viewed_position, log_date, status")
                    .fromClause("FROM daily_user_view_log")
                    .whereClause("WHERE log_date = :targetDate")
                    .parameterValues(Map.of("targetDate", targetDate))
                    .sortKeys(Collections.singletonMap("id", Order.ASCENDING))
                    .rowMapper(new BeanPropertyRowMapper<>(DailyMemberViewLog.class))
                    .build();

        } catch (Exception e) {
            log.error("Failed to create paging reader", e);
            throw new BatchConfigurationException("Failed to create daily log reader", e);
        }
    }
}
