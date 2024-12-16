package com.github.garamflow.streamsettlement.batch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDate;


@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataGenerator {

    private final JdbcTemplate jdbcTemplate;
    @Getter
    private int contentCount;

    public void initialize() {
        initializeProcedures();
    }

    public TestDataResult createTestData(TestDataConfig config, LocalDate targetDate) {
        this.contentCount = config.contentCount();
        try {
            log.info("테스트 데이터 생성 시작 - {}", config);

            jdbcTemplate.query(
                    "CALL create_test_data(?, ?, ?, ?, ?, ?)",
                    (RowCallbackHandler) rs -> {
                        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                            log.info("Data creation result: {}", rs.getString(i));
                        }
                    },
                    config.memberCount(), config.contentCount(),
                    config.adCount(), config.viewLogCount(),
                    config.adViewLogCount(), targetDate
            );

            log.info("데이터 생성 결과 검증 시작");
            
            // 1. content_post 테이블 데이터 확인
            Integer contentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM content_post", Integer.class);
            log.info("생성된 콘텐츠 수: {}", contentCount);

            // 2. 실제 조인 결과 확인
            Integer joinCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) 
                    FROM member m 
                    CROSS JOIN content_post c 
                    WHERE m.role = 'MEMBER' 
                    LIMIT 5
                    """, Integer.class);
            log.info("멤버-콘텐츠 조인 결과 수: {}", joinCount);

            // 3. member_content_watch_log의 content_post_id NULL 체크
            Integer nullContentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_content_watch_log WHERE content_post_id IS NULL",
                    Integer.class);
            log.info("content_post_id가 NULL인 시청 로그 수: {}", nullContentCount);

            if (nullContentCount > 0) {
                throw new RuntimeException("시청 로그에 content_post_id가 NULL인 레코드가 있습니다.");
            }

            return new TestDataResult(
                    getCount("member"),
                    getCount("content_post"),
                    getCount("member_content_watch_log WHERE watched_date = ?", targetDate)
            );
        } catch (Exception e) {
            log.error("테스트 데이터 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("테스트 데이터 생성 실패", e);
        }
    }

    private Integer getCount(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Integer getCount(String table, Object... params) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class,
                params
        );
    }

    private void initializeProcedures() {
        try {
            log.info("저장 프로시저 초기화 시작");

            // 기존 프로시저들 삭제
            dropExistingProcedures();

            // 프로시저 순차적 생성 (의존성 순서 고려)
            createSettlementRatesProcedure();   // 1. 정산율 (독립적)
            createMembersProcedure();           // 2. 회원 (독립적)
            createContentPostsProcedure();      // 3. 콘텐츠 (회원 의존)
            createAdvertisementsProcedure();    // 4. 광고 (회원 의존)
            createContentAdMappingsProcedure(); // 5. 매핑 (콘텐츠, 광고 의존)
            createViewLogsProcedure();          // 6. 로그 (콘텐츠 의존)
            createMainProcedure();              // 7. 메인 (모든 프로시저 의존)

            log.info("저장 프로시저 초기화 완료");
        } catch (Exception e) {
            log.error("저장 프로시저 초기화 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("저장 프로시저 초기화 실패", e);
        }
    }

    private void dropExistingProcedures() {
        String[] procedures = {
                "create_test_data",
                "create_settlement_rates",
                "create_members",
                "create_content_posts",
                "create_advertisements",
                "create_content_ad_mappings",
                "create_view_logs"
        };

        for (String procedure : procedures) {
            jdbcTemplate.execute("DROP PROCEDURE IF EXISTS " + procedure);
        }
    }

    private void createMainProcedure() {
        String sql = """
                    CREATE PROCEDURE create_test_data(
                        IN p_member_count INT,
                        IN p_content_count INT,
                        IN p_ad_count INT,
                        IN p_view_log_count INT,
                        IN p_ad_view_log_count INT,
                        IN p_target_date DATE
                    )
                    BEGIN
                        DECLARE start_time TIMESTAMP;
                        SET start_time = NOW();
                
                        SET FOREIGN_KEY_CHECKS = 0;
                
                        -- 테이블 초기화 전 데이터 수 확인
                        SELECT COUNT(*) INTO @pre_member FROM member;
                        SELECT COUNT(*) INTO @pre_content FROM content_post;
                
                        TRUNCATE TABLE member_content_watch_log;
                        TRUNCATE TABLE member_ad_watch_log;
                        TRUNCATE TABLE daily_watched_content;
                        TRUNCATE TABLE content_statistics;
                        TRUNCATE TABLE advertisement_content_post;
                        TRUNCATE TABLE advertisement;
                        TRUNCATE TABLE content_post;
                        TRUNCATE TABLE member;
                        TRUNCATE TABLE settlement;
                
                        CALL create_settlement_rates();
                        CALL create_members(p_member_count, 'CREATOR');
                        CALL create_members(p_ad_count, 'MEMBER');
                        CALL create_content_posts(p_content_count);
                        CALL create_advertisements(p_ad_count);
                        CALL create_content_ad_mappings(p_content_count, p_ad_count);
                        CALL create_view_logs(p_view_log_count, p_target_date);
                
                        -- 생성된 데이터 수 확인
                        SELECT COUNT(*) INTO @post_member FROM member;
                        SELECT COUNT(*) INTO @post_content FROM content_post;
                
                        SET FOREIGN_KEY_CHECKS = 1;
                
                        -- 결과 출력
                        SELECT
                            CONCAT('Members created: ', @post_member),
                            CONCAT('Contents created: ', @post_content),
                            CONCAT('Total execution time: ',
                                TIMESTAMPDIFF(SECOND, start_time, NOW()), ' seconds')
                        AS execution_info;
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    private void createSettlementRatesProcedure() {
        String sql = """
                    CREATE PROCEDURE create_settlement_rates()
                    BEGIN
                        INSERT INTO settlement_rate 
                        (settlement_type, min_views, max_views, rate, applied_at, created_at)
                        VALUES 
                        ('CONTENT', 0, 1000, 0.4, NOW(), NOW()),
                        ('CONTENT', 1001, 5000, 0.5, NOW(), NOW()),
                        ('CONTENT', 5001, NULL, 0.6, NOW(), NOW()),
                        ('ADVERTISEMENT', 0, 1000, 0.3, NOW(), NOW()),
                        ('ADVERTISEMENT', 1001, 5000, 0.4, NOW(), NOW()),
                        ('ADVERTISEMENT', 5001, NULL, 0.5, NOW(), NOW());
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    private void createMembersProcedure() {
        String sql = """
                    CREATE PROCEDURE create_members(IN p_count INT, IN p_role VARCHAR(20))
                    BEGIN
                        DECLARE i INT DEFAULT 0;
                        WHILE i < p_count DO
                            INSERT INTO member (
                                email, username, provider, provider_id, 
                                role, created_at, updated_at
                            ) VALUES (
                                CONCAT(LOWER(p_role), i, '@test.com'),
                                CONCAT(LOWER(p_role), i),
                                'test',
                                CONCAT('testId', i),
                                p_role,
                                NOW(),
                                NOW()
                            );
                            SET i = i + 1;
                        END WHILE;
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    private void createContentPostsProcedure() {
        String sql = """
                    CREATE PROCEDURE create_content_posts(IN p_count INT) 
                    BEGIN
                        DECLARE i INT DEFAULT 0;
                        DECLARE creator_count INT;
                
                        SELECT COUNT(*) INTO creator_count 
                        FROM member 
                        WHERE role = 'CREATOR';
                
                        WHILE i < p_count DO
                            INSERT INTO content_post (
                                member_id, title, description, duration,
                                total_views, total_watch_time, url, status,
                                created_at, updated_at
                            )
                            VALUES (
                                (SELECT member_id 
                                 FROM member 
                                 WHERE role = 'CREATOR' 
                                 ORDER BY RAND() 
                                 LIMIT 1),
                                CONCAT('Content ', i),
                                CONCAT('Description for content ', i),
                                FLOOR(60 + RAND() * 540),
                                0,
                                0,
                                CONCAT('https://example.com/video/', i),
                                'ACTIVE',
                                NOW(),
                                NOW()
                            );
                            SET i = i + 1;
                        END WHILE;
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    private void createAdvertisementsProcedure() {
        String sql = """
                    CREATE PROCEDURE create_advertisements(IN p_count INT)
                    BEGIN
                        DECLARE i INT DEFAULT 0;
                
                        WHILE i < p_count DO
                            INSERT INTO advertisement (
                                advertiser_id, title, description,
                                price_per_view, total_views, created_at, updated_at
                            )
                            VALUES (
                                (SELECT member_id 
                                 FROM member 
                                 WHERE role = 'MEMBER' 
                                 ORDER BY RAND() 
                                 LIMIT 1),
                                CONCAT('Ad ', i),
                                CONCAT('Ad Description ', i),
                                100 + FLOOR(RAND() * 900),
                                0,
                                NOW(),
                                NOW()
                            );
                            SET i = i + 1;
                        END WHILE;
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    private void createContentAdMappingsProcedure() {
        String sql = """
                    CREATE PROCEDURE create_content_ad_mappings(
                        IN p_content_count INT,
                        IN p_ad_count INT
                    )
                    BEGIN
                        DECLARE i INT DEFAULT 0;
                        DECLARE j INT;
                        DECLARE ad_limit INT;
                
                        WHILE i < p_content_count DO
                            SET ad_limit = 1 + FLOOR(RAND() * 3);
                            SET j = 0;
                
                            WHILE j < ad_limit DO
                                INSERT INTO advertisement_content_post (
                                    advertisement_id,
                                    content_post_id
                                )
                                SELECT 
                                    advertisement_id,
                                    (
                                        SELECT content_post_id 
                                        FROM content_post 
                                        ORDER BY content_post_id 
                                        LIMIT i,1
                                    )
                                FROM (
                                    SELECT advertisement_id 
                                    FROM advertisement 
                                    ORDER BY RAND() 
                                    LIMIT 1
                                ) AS random_ad;
                
                                SET j = j + 1;
                            END WHILE;
                
                            SET i = i + 1;
                        END WHILE;
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    private void createViewLogsProcedure() {
        String sql = """
                    CREATE PROCEDURE create_view_logs(
                        IN p_count INT, IN p_target_date DATE
                    )
                    BEGIN
                        DECLARE batch_size INT DEFAULT 1000;
                        DECLARE i INT DEFAULT 0;
                
                        WHILE i < p_count DO
                            -- 시청 로그 생성
                            INSERT INTO member_content_watch_log (
                                member_id, content_post_id, last_playback_position,
                                total_playback_time, watched_date, streaming_status,
                                created_at, updated_at
                            )
                            SELECT 
                                m.member_id,
                                c.content_post_id,
                                300,
                                250,
                                p_target_date,
                                'COMPLETED',
                                NOW(),
                                NOW()
                            FROM member m
                            CROSS JOIN content_post c
                            WHERE m.role = 'MEMBER'
                            ORDER BY RAND()
                            LIMIT batch_size;
                
                            -- daily_watched_content 테이블에 데이터 삽입
                            INSERT IGNORE INTO daily_watched_content (
                                content_post_id, watched_date, created_at
                            )
                            SELECT 
                                mcwl.content_post_id,
                                mcwl.watched_date,
                                NOW()
                            FROM (
                                SELECT DISTINCT content_post_id, watched_date
                                FROM member_content_watch_log
                                WHERE watched_date = p_target_date
                                AND NOT EXISTS (
                                    SELECT 1 
                                    FROM daily_watched_content dwc 
                                    WHERE dwc.content_post_id = member_content_watch_log.content_post_id
                                    AND dwc.watched_date = member_content_watch_log.watched_date
                                )
                            ) mcwl;
                
                            -- 콘텐츠 조회수 업데이트
                            UPDATE content_post c
                            INNER JOIN (
                                SELECT 
                                    content_post_id, 
                                    COUNT(*) as view_count
                                FROM member_content_watch_log
                                WHERE watched_date = p_target_date
                                GROUP BY content_post_id
                            ) l ON c.content_post_id = l.content_post_id
                            SET 
                                c.total_views = c.total_views + l.view_count,
                                c.total_watch_time = c.total_watch_time + (l.view_count * 250);
                
                            SET i = i + batch_size;
                        END WHILE;
                    END
                """;
        jdbcTemplate.execute(sql);
    }

    public record TestDataConfig(
            int memberCount,
            int contentCount,
            int adCount,
            int viewLogCount,
            int adViewLogCount
    ) {
    }

    public record TestDataResult(
            int actualMembers,
            int actualContents,
            int actualViews
    ) {
    }

    public void cleanupTables() {
        log.info("테이블 초기화 시작");
        String[] tables = {
                "settlement",
                "content_statistics",
                "member_content_watch_log",
                "advertisement_content_post",
                "advertisement",
                "content_post",
                "member",
                "settlement_rate"
        };

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : tables) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
            } catch (Exception e) {
                log.warn("테이블 초기화 중 오류 발생 {}: {}", table, e.getMessage());
            }
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        log.info("테이블 초기화 완료");
    }

}
