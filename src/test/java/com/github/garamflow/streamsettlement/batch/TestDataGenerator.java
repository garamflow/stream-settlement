package com.github.garamflow.streamsettlement.batch;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataGenerator {

    private final EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void createTestData(int memberCount, int contentCount, int adCount, int viewLogCount) {
        initializeDatabase();
        createProcedure();
        executeDataGeneration(memberCount, contentCount, adCount, viewLogCount);
    }

    @Transactional
    public void clearAllData() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE daily_member_view_log");
        // ... 다른 테이블들도 TRUNCATE
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
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

    private void createProcedure() {
        entityManager.createNativeQuery("DROP PROCEDURE IF EXISTS generate_test_data").executeUpdate();
        entityManager.createNativeQuery(createProcedureSQL()).executeUpdate();
    }

    private void executeDataGeneration(int memberCount, int contentCount, int adCount, int viewLogCount) {
        entityManager.createNativeQuery(
                        "CALL generate_test_data(:memberCount, :contentCount, :adCount, :viewLogCount)")
                .setParameter("memberCount", memberCount)
                .setParameter("contentCount", contentCount)
                .setParameter("adCount", adCount)
                .setParameter("viewLogCount", viewLogCount)
                .executeUpdate();

        entityManager.flush();
        entityManager.clear();
    }

    private String createProcedureSQL() {
        return """
                                                CREATE PROCEDURE generate_test_data(
                    IN p_member_count INT,
                    IN p_content_count INT,
                    IN p_ad_count INT,
                    IN p_view_log_count INT
                )
                BEGIN
                    DECLARE viewed_log_count INT;
                    DECLARE zero_view_count INT;
                    DECLARE counter INT DEFAULT 1;
                
                    SET viewed_log_count = CEIL(p_view_log_count * 0.3);
                    SET zero_view_count = p_view_log_count - viewed_log_count;
                
                    -- 멤버 생성 (동일)
                    INSERT INTO member (role, username, email, created_at)
                    SELECT 
                        'MEMBER',
                        CONCAT('user ', numbers.n),
                        CONCAT('user', numbers.n, '_', UNIX_TIMESTAMP(), '@test.com'),
                        NOW()
                    FROM (
                        SELECT a.N + b.N * 10 + c.N * 100 + 1 as n
                        FROM 
                            (SELECT 0 as N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
                            (SELECT 0 as N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
                            (SELECT 0 as N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
                        ORDER BY n
                        LIMIT p_member_count
                    ) numbers;
                
                    -- 콘텐츠 생성 (동일)
                    INSERT INTO content_post (
                        member_id, title, description, status, url, 
                        duration, total_views, total_watch_time, created_at
                    )
                    SELECT 
                        m.member_id,
                        CONCAT('Video ', numbers.n),
                        CONCAT('Description for video ', numbers.n),
                        'ACTIVE',
                        CONCAT('https://test.com/video/', numbers.n),
                        300 + FLOOR(RAND() * 301),
                        0,
                        0,
                        NOW()
                    FROM (
                        SELECT a.N + b.N * 10 + c.N * 100 + 1 as n
                        FROM 
                            (SELECT 0 as N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
                            (SELECT 0 as N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
                            (SELECT 0 as N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
                        ORDER BY n
                        LIMIT p_content_count
                    ) numbers
                    CROSS JOIN (SELECT member_id FROM member ORDER BY RAND() LIMIT 1) m;
                
                    -- 광고 생성 (동일)
                    WHILE counter <= p_ad_count DO
                        INSERT INTO advertisement (
                            title, description, price_per_view, total_views, created_at
                        )
                        VALUES (
                            CONCAT('Ad ', counter),
                            'Test advertisement description',
                            50 + FLOOR(RAND() * 51),
                            0,
                            NOW()
                        );
                        SET counter = counter + 1;
                    END WHILE;
                
                    -- 실제 조회된 로그 생성 (30%)
                    INSERT INTO daily_member_view_log (
                        member_id, content_post_id, last_viewed_position,
                        last_ad_view_count, log_date, streaming_status, created_at
                    )
                    SELECT 
                        m.member_id,
                        c.content_post_id,
                        FLOOR(60 + RAND() * 3540),
                        FLOOR(1 + RAND() * 11),
                        CURDATE(),
                        'COMPLETED',
                        DATE_ADD(NOW(), INTERVAL - FLOOR(RAND() * 24) HOUR)
                    FROM 
                        (SELECT member_id FROM member ORDER BY RAND()) m
                        CROSS JOIN (SELECT content_post_id FROM content_post ORDER BY RAND()) c
                    LIMIT viewed_log_count;
                
                    -- 조회되지 않은 로그 생성 (70%)
                    INSERT INTO daily_member_view_log (
                        member_id, content_post_id, last_viewed_position,
                        last_ad_view_count, log_date, streaming_status, created_at
                    )
                    SELECT 
                        m.member_id,
                        c.content_post_id,
                        0,
                        0,
                        CURDATE(),
                        'COMPLETED',
                        DATE_ADD(NOW(), INTERVAL - FLOOR(RAND() * 24) HOUR)
                    FROM 
                        (SELECT member_id FROM member ORDER BY RAND()) m
                        CROSS JOIN (SELECT content_post_id FROM content_post ORDER BY RAND()) c
                    WHERE NOT EXISTS (
                        SELECT 1 FROM daily_member_view_log d 
                        WHERE d.member_id = m.member_id 
                        AND d.content_post_id = c.content_post_id
                    )
                    LIMIT zero_view_count;
                END
                """;
    }
}
