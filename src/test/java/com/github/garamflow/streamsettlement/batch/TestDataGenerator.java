package com.github.garamflow.streamsettlement.batch;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataGenerator {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final MemberRepository memberRepository;
    private final ContentPostRepository contentPostRepository;

    @Transactional
    public void createTestData(int memberCount, int contentCount, int adCount, int viewLogCount) {
        try {
            clearAllData();
            createProcedure();
            executeDataGeneration(memberCount, contentCount, adCount, viewLogCount);
            entityManager.flush();  // 명시적 flush 추가
        } catch (Exception e) {
            log.error("테스트 데이터 생성 중 오류 발생", e);
            throw e;  // 예외를 다시 던져서 실패를 명확히 함
        }
    }

    @Transactional
    public void clearAllData() {
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("TRUNCATE TABLE member_content_watch_log");
            jdbcTemplate.execute("TRUNCATE TABLE member_ad_watch_log");
            jdbcTemplate.execute("TRUNCATE TABLE daily_watched_content");
            jdbcTemplate.execute("TRUNCATE TABLE content_statistics");
            jdbcTemplate.execute("TRUNCATE TABLE advertisement_content_post");
            jdbcTemplate.execute("TRUNCATE TABLE advertisement");
            jdbcTemplate.execute("TRUNCATE TABLE content_post");
            jdbcTemplate.execute("TRUNCATE TABLE member");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception e) {
            log.error("데이터 초기화 중 오류 발생", e);
            throw e;
        }
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
                    IN member_count INT,
                    IN content_count INT,
                    IN ad_count INT,
                    IN view_log_count INT
                )
                BEGIN
                    DECLARE i INT DEFAULT 1;
                    DECLARE j INT;
                    DECLARE content_id INT;
                    DECLARE ad_id INT;
                    DECLARE member_id INT;
                
                    -- member 데이터 생성
                    SET i = 1;
                    WHILE i <= member_count DO
                        INSERT INTO member (
                            member_id,
                            email,
                            username,
                            role,
                            created_at,
                            updated_at,
                            provider,
                            provider_id
                        ) VALUES (
                            i,
                            CONCAT('user', i, '@example.com'),
                            CONCAT('User', i),
                            'MEMBER',
                            NOW(),
                            NOW(),
                            'LOCAL',
                            CONCAT('ID', i)
                        );
                        SET i = i + 1;
                    END WHILE;
                
                    -- 컨텐츠 생성
                    SET i = 1;
                    WHILE i <= content_count DO
                        INSERT INTO content_post (
                            content_post_id,
                            member_id,
                            title,
                            description,
                            url,
                            duration,
                            total_views,
                            total_watch_time,
                            created_at,
                            updated_at,
                            status,
                            category
                        ) VALUES (
                            i,
                            1,
                            CONCAT('Content ', i),
                            CONCAT('Description ', i),
                            CONCAT('url', i),
                            3600,
                            0,
                            0,
                            NOW(),
                            NOW(),
                            'ACTIVE',
                            'VIDEO'
                        );
                        SET i = i + 1;
                    END WHILE;
                
                    -- 광고 생성
                    SET i = 1;
                    WHILE i <= ad_count DO
                        INSERT INTO advertisement (
                            advertisement_id,
                            title,
                            description,
                            price_per_view,
                            total_views,
                            created_at,
                            updated_at
                        ) VALUES (
                            i,
                            CONCAT('Ad ', i),
                            CONCAT('Ad Description ', i),
                            100,
                            0,
                            NOW(),
                            NOW()
                        );
                
                        -- 광고와 컨텐츠 매핑
                        INSERT INTO advertisement_content_post (
                            advertisement_id,
                            content_post_id
                        ) VALUES (
                            i,
                            1
                        );
                        SET i = i + 1;
                    END WHILE;
                END
                """;
    }

    @Transactional
    public List<ContentPost> createTestContentPosts(int count) {
        List<ContentPost> contentPosts = new ArrayList<>();

        // 테스트용 Member 생성 - 이메일을 유니크하게 생성
        Member member = Member.builder()
                .email("test" + System.currentTimeMillis() + "@test.com") // 유니크한 이메일
                .username("testUser")
                .provider("test")
                .providerId("testId")
                .role(Role.CREATOR)
                .build();

        memberRepository.save(member);

        for (int i = 0; i < count; i++) {
            ContentPost contentPost = ContentPost.builder()
                    .member(member)
                    .title("Test Content " + i)
                    .url("http://test.com")
                    .description("Test Description " + i)
                    .totalViews(0L)
                    .build();

            contentPosts.add(contentPostRepository.save(contentPost));
        }

        return contentPosts;
    }
}
