package com.github.garamflow.streamsettlement.batch.performance;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@SpringBatchTest
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DailyLogAggregationPerformanceTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private ThreadPoolTaskExecutor taskExecutor;

    @BeforeAll
    void setUpOnce() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(__ -> {
            initializeDatabase();
            createTestData();
            return null;
        });
    }

    private void initializeDatabase() {
        // 외래키 제약조건 비활성화
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        // 모든 테이블 초기화
        entityManager.createNativeQuery("TRUNCATE TABLE content_statistics").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE daily_member_view_log").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE advertisement_content_post").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE advertisement").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE content_post").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE member").executeUpdate();

        // 외래키 제약조건 다시 활성화
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }

    private void createTestData() {
        // 프로시저 생성 코드는 그대로 유지
        entityManager.createNativeQuery("DROP PROCEDURE IF EXISTS generate_test_data").executeUpdate();
        String createProcedure = """
                CREATE PROCEDURE generate_test_data(
                        IN p_member_count INT,
                        IN p_content_count INT,
                        IN p_ad_count INT,
                        IN p_view_log_count INT
                    )
                    BEGIN
                        DECLARE half_members INT;
                        DECLARE remaining_members INT;
                        DECLARE mapping_limit INT;
                        DECLARE i INT DEFAULT 1;
                
                        SET half_members = p_member_count / 2;
                        SET remaining_members = p_member_count - half_members;
                        SET mapping_limit = p_content_count * 2;
                
                        -- 멤버 생성 (크리에이터)
                        SET i = 1;
                        WHILE i <= half_members DO
                            INSERT INTO member (role, username, email, created_at)
                            VALUES (
                                'CREATOR',
                                CONCAT('creator ', i),
                                CONCAT('creator', i, '_', UNIX_TIMESTAMP(), '@test.com'),
                                NOW()
                            );
                            SET i = i + 1;
                        END WHILE;
                
                        -- 멤버 생성 (일반 사용자)
                        SET i = 1;
                        WHILE i <= remaining_members DO
                            INSERT INTO member (role, username, email, created_at)
                            VALUES (
                                'MEMBER',
                                CONCAT('user ', i),
                                CONCAT('user', i, '_', UNIX_TIMESTAMP(), '@test.com'),
                                NOW()
                            );
                            SET i = i + 1;
                        END WHILE;
                
                        -- 콘텐츠 생
                        SET i = 1;
                        WHILE i <= p_content_count DO
                            INSERT INTO content_post (
                                member_id, title, description, status, url, 
                                duration, total_views, total_watch_time, created_at
                            )
                            SELECT 
                                m.member_id,
                                CONCAT('Video ', i),
                                CONCAT('Description for video ', i),
                                'ACTIVE',
                                CONCAT('https://test.com/video/', i),
                                300 + FLOOR(RAND() * 301),
                                0,
                                0,
                                NOW()
                            FROM (SELECT member_id FROM member WHERE role = 'CREATOR' ORDER BY RAND() LIMIT 1) m;
                            SET i = i + 1;
                        END WHILE;
                
                        -- 광고 ��성
                        SET i = 1;
                        WHILE i <= p_ad_count DO
                            INSERT INTO advertisement (
                                title, description, price_per_view, total_views, created_at
                            )
                            VALUES (
                                CONCAT('Ad ', i),
                                'Test advertisement description',
                                50 + FLOOR(RAND() * 51),
                                0,
                                NOW()
                            );
                            SET i = i + 1;
                        END WHILE;
                
                        -- 광고-콘텐츠 매핑
                        INSERT INTO advertisement_content_post (advertisement_id, content_post_id)
                        SELECT DISTINCT
                            a.advertisement_id,
                            c.content_post_id
                        FROM content_post c
                        CROSS JOIN advertisement a
                        WHERE RAND() < 0.7
                        LIMIT mapping_limit;
                
                        -- 조회 로그 생성
                        SET i = 1;
                        WHILE i <= p_view_log_count DO
                            INSERT INTO daily_member_view_log (
                                member_id, 
                                content_post_id,
                                last_viewed_position,
                                last_ad_view_count,
                                log_date,
                                streaming_status,
                                created_at
                            )
                            SELECT
                                m.member_id,
                                c.content_post_id,
                                FLOOR(RAND() * 3600),
                                FLOOR(RAND() * 12),
                                DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 30) DAY),
                                ELT(1 + FLOOR(RAND() * 4), 'IN_PROGRESS', 'PAUSED', 'STOPPED', 'COMPLETED'),
                                NOW()
                            FROM 
                                member m
                                CROSS JOIN content_post c
                                CROSS JOIN (SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL 
                                           SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL 
                                           SELECT 9 UNION ALL SELECT 10) numbers
                            WHERE m.role = 'MEMBER' AND c.status = 'ACTIVE'
                            ORDER BY RAND()
                            LIMIT 1000;  -- 한 번에 1000개씩 삽입
                
                            SET i = i + 1000;  -- 증가값을 1000으로 설정
                        END WHILE;
                    END
                """;
        entityManager.createNativeQuery(createProcedure).executeUpdate();

        // 데이터 생성 실행
        entityManager.createNativeQuery("CALL generate_test_data(:memberCount, :contentCount, :adCount, :viewLogCount)")
                .setParameter("memberCount", 100)
                .setParameter("contentCount", 1000)
                .setParameter("adCount", 20)
                .setParameter("viewLogCount", 10000)
//                .setParameter("viewLogCount", 100000)
//                .setParameter("viewLogCount", 500000)
//                .setParameter("viewLogCount", 1000000)
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();
    }

    @BeforeEach
    void verifyTestData() {
        // 데이터가 존재하는지 확인
        Long viewLogCount = entityManager.createQuery(
                        "SELECT COUNT(d) FROM DailyMemberViewLog d", Long.class)
                .getSingleResult();

        if (viewLogCount == 0) {
            log.info("테스트 데이터 재생성 시작");
            createTestData();
            log.info("테스트 데이터 재생성 완료");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 50, 100, 500, 1000})
    @DisplayName("청크 사이즈별 성능 테스트")
    void chunkSizePerformanceTest(int chunkSize) throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        StopWatch stopWatch = new StopWatch();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        // when
        stopWatch.start();
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addString("chunkSize", String.valueOf(chunkSize))
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        stopWatch.stop();
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 성능 메트릭 출력
        logPerformanceMetrics(
                chunkSize,
                stopWatch.getTotalTimeSeconds(),
                (endMemory - startMemory) / (1024 * 1024), // MB 단위
                jobExecution.getStepExecutions().iterator().next().getReadCount()
        );
    }

    private void logPerformanceMetrics(int chunkSize, double totalTimeSeconds, long memoryUsedMB, long processedRecords) {
        log.info("\n");
        log.info("┌──────────────────────────────────────────┐");
        log.info("│            성능 테스트 결과                │");
        log.info("├──────────────────────────────────────────┤");
        log.info("│ 청크 사이즈: {}", String.format("%-28d", chunkSize) + "│");
        log.info("│ 총 처리 시간: {} 초", String.format("%-24.2f", totalTimeSeconds) + "│");
        log.info("│ 초당 처리 건수: {} records/sec",
                String.format("%-18.2f", processedRecords / totalTimeSeconds) + "│");
        log.info("│ 메모리 사용량: {} MB", String.format("%-24d", memoryUsedMB) + "│");
        log.info("│ 총 처리 건수: {}", String.format("%-26d", processedRecords) + "│");
        log.info("│ CPU 사용률: {}%",
                String.format("%-27.2f", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()) + "│");
        log.info("└──────────────────────────────────────────┘");
        log.info("\n");
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 20, 50})
    @DisplayName("동시 실행 부하 테스트")
    void loadTest(int threadCount) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(threadCount);
        taskExecutor.setMaxPoolSize(threadCount);
        taskExecutor.setQueueCapacity(threadCount * 2);
        taskExecutor.setThreadNamePrefix("test-thread-");
        taskExecutor.initialize();

        CountDownLatch latch = new CountDownLatch(threadCount);
        List<JobExecution> executions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        JobParameters params = new JobParametersBuilder()
                                .addString("targetDate", LocalDate.now().toString())
                                .addString("chunkSize", "500")
                                .addString("thread", String.valueOf(index))
                                .addLong("timestamp", System.currentTimeMillis())
                                .toJobParameters();

                        JobExecution execution = jobLauncherTestUtils.launchJob(params);
                        executions.add(execution);
                    } catch (Exception e) {
                        log.error("Job 실행 실패 (thread-{})", index, e);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        latch.await(15, TimeUnit.MINUTES);
        stopWatch.stop();

        logLoadTestMetrics(threadCount, stopWatch.getTotalTimeSeconds(), executions.size());
        taskExecutor.shutdown();
    }

    private void logLoadTestMetrics(int threadCount, double totalTime, int successCount) {
        log.info("\n");
        log.info("┌──────────────────────────────────────────┐");
        log.info("│            부하 테스트 결과                │");
        log.info("├──────────────────────────────────────────┤");
        log.info("│ 스레드 수: {}", String.format("%-28d", threadCount) + "│");
        log.info("│ 총 처리 시간: {} 초", String.format("%-24.2f", totalTime) + "│");
        log.info("│ 초당 처리 건수: {} jobs/sec",
                String.format("%-22.2f", successCount / totalTime) + "│");
        log.info("│ DB 커넥션 수: {}",
                String.format("%-26d", ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()) + "│");
        log.info("│ 스레드 풀 활성도: {}%",
                String.format("%-23.2f", taskExecutor.getActiveCount() * 100.0 / threadCount) + "│");
        log.info("└──────────────────────────────────────────┘");
        log.info("\n");
    }
}
