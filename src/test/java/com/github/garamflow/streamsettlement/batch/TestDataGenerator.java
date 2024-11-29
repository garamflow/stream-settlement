package com.github.garamflow.streamsettlement.batch;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataGenerator {

    private final EntityManager entityManager;

    @Transactional
    public void createTestData(int memberCount, int contentCount, int adCount, int viewLogCount) {
        initializeDatabase();
        createProcedure();
        executeDataGeneration(memberCount, contentCount, adCount, viewLogCount);
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
                
                        -- 광고 생성
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
                                CURDATE(),
                                'COMPLETED',
                                NOW()
                            FROM 
                                member m,
                                content_post c
                            WHERE 
                                m.role = 'MEMBER' 
                                AND c.status = 'ACTIVE'
                                AND c.content_post_id <= p_content_count
                            ORDER BY RAND()
                            LIMIT 1000;
                
                            SET i = i + 1000;
                        END WHILE;
                        
                        -- 데이터 검증을 위한 카운트 출력
                        SELECT COUNT(*) as total_logs FROM daily_member_view_log WHERE log_date = CURDATE();
                    END
                """;
    }
}
