package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class BatchPerformanceTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job dailyLogAggregationJob;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BatchProperties batchProperties;

    @BeforeEach
    void setUp() {
        testDataGenerator.clearAllData();
    }

    @DisplayName("배치 처리 성능 테스트")
    @ParameterizedTest
    @ValueSource(ints = {100000})
        // 데이터 크기별 테스트
    void batchPerformanceTest(int dataSize) throws Exception {
        // Given
        generateTestData(dataSize);

        // 데이터 현황 상세 로깅
        String countSql = """
                SELECT
                    COUNT(*) as total_count,
                    SUM(CASE WHEN last_viewed_position = 0 THEN 1 ELSE 0 END) as zero_view_count,
                    SUM(CASE WHEN last_viewed_position > 0 THEN 1 ELSE 0 END) as non_zero_view_count,
                    MIN(last_viewed_position) as min_position,
                    MAX(last_viewed_position) as max_position,
                    AVG(last_viewed_position) as avg_position
                FROM daily_member_view_log
                WHERE log_date = ?
                """;

        jdbcTemplate.query(
                countSql,
                ps -> ps.setObject(1, LocalDate.now()),
                rs -> {
                    log.info("""
                                    
                                    테스트 데이터 현황:
                                    전체 데이터: {}
                                    조회수 0인 데이터: {} ({}%)
                                    조회수 있는 데이터: {} ({}%)
                                    조회 위치 범위: {} ~ {} (평균: {})
                                    """,
                            rs.getInt("total_count"),
                            rs.getInt("zero_view_count"),
                            String.format("%.1f", rs.getInt("zero_view_count") * 100.0 / rs.getInt("total_count")),
                            rs.getInt("non_zero_view_count"),
                            String.format("%.1f", rs.getInt("non_zero_view_count") * 100.0 / rs.getInt("total_count")),
                            rs.getInt("min_position"),
                            rs.getInt("max_position"),
                            String.format("%.1f", rs.getDouble("avg_position"))
                    );
                }
        );

        // When
        JobParameters parameters = new JobParametersBuilder()
                .addString("targetDate", LocalDate.now().toString())
                .addString("gridSize", String.valueOf(batchProperties.getMaxGridSize()))
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        long startTime = System.currentTimeMillis();
        JobExecution execution = jobLauncher.run(dailyLogAggregationJob, parameters);
        long endTime = System.currentTimeMillis();

        // Then
        double executionTime = (endTime - startTime) / 1000.0;  // 초 단위
        int processedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_statistics", Integer.class);
        double tps = processedCount / executionTime;

        log.info("""
                        
                        성능 테스트 결과 (데이터 크기: {})
                        총 실행 시간: {}초
                        처리된 레코드 수: {}
                        TPS: {}
                        작업 상태: {}""",
                dataSize,
                String.format("%.2f", executionTime),
                processedCount,
                String.format("%.2f", tps),
                execution.getStatus());

        assertThat(execution.getStatus().isUnsuccessful()).isFalse();
        assertThat(processedCount).isGreaterThan(0);
    }

    private void generateTestData(int size) {
        // 기본 테스트 데이터 생성
        testDataGenerator.createTestData(
                size / 100,  // 멤버 수
                size / 10, // 콘텐츠 수
                size / 100,  // 광고 수
                size         // 조회 로그 수
        );
    }
} 