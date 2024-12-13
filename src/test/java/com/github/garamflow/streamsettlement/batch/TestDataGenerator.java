package com.github.garamflow.streamsettlement.batch;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.advertisement.Advertisement;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentStatus;
import com.github.garamflow.streamsettlement.repository.advertisement.AdvertisementRepository;
import com.github.garamflow.streamsettlement.repository.member.MemberRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final MemberRepository memberRepository;
    private final ContentPostRepository contentPostRepository;
    private final AdvertisementRepository advertisementRepository;

    @Transactional
    public void createTestData(
            int memberCount,
            int contentCount,
            int adCount,
            int viewLogCount,
            int adViewLogCount
    ) {
        jdbcTemplate.execute("ALTER TABLE member_content_watch_log DISABLE KEYS");
        jdbcTemplate.execute("ALTER TABLE member_ad_watch_log DISABLE KEYS");

        try {
            clearAllData();
            log.info("데이터 생성 시작");

            // 1. 정산율 생성
            createSettlementRates();
            log.info("정산율 생성 완료");

            // 1. 회원 생성 (크리에이터 + 광고주)
            List<Member> creators = createMembers(memberCount, Role.CREATOR);
            List<Member> advertisers = createMembers(adCount, Role.MEMBER);
            log.info("회원 생성 완료: 크리에이터 {}, 광고주 {}", memberCount, adCount);

            // 2. 콘텐츠 생성
            List<ContentPost> contents = createContentPosts(contentCount, creators);
            log.info("콘텐츠 생성 완료: {}", contentCount);

            // 3. 광고 생성
            List<Advertisement> ads = createAdvertisements(adCount, advertisers);
            log.info("광고 생성 완료: {}", adCount);

            // 4. 콘텐츠-광고 매핑
            createContentAdMappings(contents, ads);
            log.info("콘텐츠-광고 매핑 완료");

            // 5. 시청 로그 생성
            createContentViewLogs(viewLogCount, contents, memberCount);
            createAdViewLogs(adViewLogCount, contents, ads, memberCount);
            log.info("시청 로그 생성 완료: 콘텐츠 {}, 광고 {}", viewLogCount, adViewLogCount);

        } finally {
            jdbcTemplate.execute("ALTER TABLE member_content_watch_log ENABLE KEYS");
            jdbcTemplate.execute("ALTER TABLE member_ad_watch_log ENABLE KEYS");
        }
    }

    private void createContentAdMappings(List<ContentPost> contents, List<Advertisement> ads) {
        // ads 리스���가 비어있을 때는 매핑 생성 건너뛰기
        if (ads.isEmpty()) {
            return;
        }

        Random random = new Random();
        for (ContentPost content : contents) {
            int adCount = random.nextInt(3) + 1;
            for (int i = 0; i < adCount; i++) {
                Advertisement ad = ads.get(random.nextInt(ads.size()));
                createAdvertisementContentPost(content, ad);
            }
        }
    }

    private void createAdViewLogs(int count, List<ContentPost> contents, List<Advertisement> ads, int memberCount) {
        if (count == 0) return;

        String sql = "INSERT INTO member_ad_watch_log " +
                "(member_id, content_post_id, advertisement_id, playback_position, " +
                "watched_date, streaming_status, created_at) " +  // playback_time 제거
                "VALUES (?, ?, ?, ?, ?, ?, NOW())";

        int batchSize = 1000;
        Random random = new Random();
        LocalDate targetDate = LocalDate.now().minusDays(1);

        for (int i = 0; i < count; i += batchSize) {
            final int startIndex = i;
            int currentBatch = Math.min(batchSize, count - i);

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int j) throws SQLException {
                    ContentPost content = contents.get(random.nextInt(contents.size()));
                    Advertisement ad = ads.get(random.nextInt(ads.size()));
                    long memberId = random.nextInt(memberCount) + 1;
                    int playbackPosition = random.nextInt(30) + 1;

                    ps.setLong(1, memberId);
                    ps.setLong(2, content.getId());
                    ps.setLong(3, ad.getId());
                    ps.setLong(4, playbackPosition);
                    ps.setDate(5, java.sql.Date.valueOf(targetDate));
                    ps.setString(6, StreamingStatus.COMPLETED.name());
                }

                @Override
                public int getBatchSize() {
                    return currentBatch;
                }
            });
        }
    }

    private List<Member> createMembers(int count, Role role) {
        List<Member> members = new ArrayList<>();
        int batchSize = 1000;

        for (int i = 0; i < count; i += batchSize) {
            final int startIndex = i;
            int currentBatch = Math.min(batchSize, count - i);
            String sql = "INSERT INTO member (email, username, provider, provider_id, role, created_at, updated_at) " +
                    "VALUES (?, ?, 'test', ?, ?, NOW(), NOW())";

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int j) throws SQLException {
                    int idx = startIndex + j;
                    ps.setString(1, role.name().toLowerCase() + idx + "@test.com");
                    ps.setString(2, role.name().toLowerCase() + idx);
                    ps.setString(3, "testId" + idx);
                    ps.setString(4, role.name());
                }

                @Override
                public int getBatchSize() {
                    return currentBatch;
                }
            });
        }

        return memberRepository.findAll();
    }

    private List<Advertisement> createAdvertisements(int count, List<Member> advertisers) {
        if (count == 0) return new ArrayList<>();

        String sql = "INSERT INTO advertisement " +
                "(advertiser_id, title, description, price_per_view, total_views, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 0, NOW(), NOW())";

        Random random = new Random();

        for (int i = 0; i < count; i++) {
            Member advertiser = advertisers.get(i % advertisers.size());
            jdbcTemplate.update(sql,
                    advertiser.getId(),
                    "Ad " + i,
                    "Ad Description " + i,
                    random.nextInt(1000) + 100L
            );
        }

        return advertisementRepository.findAll();
    }

    private void createContentViewLogs(int count, List<ContentPost> contents, int memberCount) {
        if (count == 0) return;

        String insertSql = "INSERT INTO member_content_watch_log " +
                "(member_id, content_post_id, last_playback_position, total_playback_time, " +
                "watched_date, streaming_status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW())";

        String updateSql = "UPDATE content_post " +
                "SET total_views = total_views + 1, " +
                "total_watch_time = total_watch_time + ? " +
                "WHERE content_post_id = ?";

        Random random = new Random();
        LocalDate targetDate = LocalDate.now().minusDays(1);

        // 각 콘텐츠별로 정확히 memberCount만큼의 시청 로그 생성
        for (ContentPost content : contents) {
            for (int i = 1; i <= memberCount; i++) {
                // 시청 로그 생성
                jdbcTemplate.update(insertSql,
                        i,
                        content.getId(),
                        300L,
                        250L,
                        java.sql.Date.valueOf(targetDate),
                        StreamingStatus.COMPLETED.name()
                );

                // 콘텐츠의 누적 조회수와 시청 시간 업데이트
                jdbcTemplate.update(updateSql,
                        250L,  // total_watch_time 증가량
                        content.getId()
                );
            }
        }
    }

    @Transactional
    public void clearAllData() {
        try {
            log.info("테이블 초기화 시작");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

            // 로그 테이블 초기화
            jdbcTemplate.execute("TRUNCATE TABLE member_content_watch_log");
            jdbcTemplate.execute("TRUNCATE TABLE member_ad_watch_log");
            jdbcTemplate.execute("TRUNCATE TABLE daily_watched_content");

            // 통계 테이블 초기화
            jdbcTemplate.execute("TRUNCATE TABLE content_statistics");

            // 광고 관련 테이블 초기화
            jdbcTemplate.execute("TRUNCATE TABLE advertisement_content_post");
            jdbcTemplate.execute("TRUNCATE TABLE advertisement");

            // 콘텐츠 및 회원 테이블 초기화
            jdbcTemplate.execute("TRUNCATE TABLE content_post");
            jdbcTemplate.execute("TRUNCATE TABLE member");

            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            log.info("테이블 초기화 완료");
        } catch (Exception e) {
            log.error("데이터 초기화 중 오류 발생", e);
            throw e;
        }
    }

    @Transactional
    public List<ContentPost> createTestContentPosts(int count) {
        List<ContentPost> contentPosts = new ArrayList<>();

        // 테스트용 Member 생성 - 이메일을 유크하게 생성
        Member member = Member.builder()
                .email("test" + System.currentTimeMillis() + "@test.com")
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

    @Async
    public CompletableFuture<Void> createWatchLogsAsync(int start, int end) {
        // 시청 로그 일부 생성
        return CompletableFuture.completedFuture(null);
    }

    private List<ContentPost> createContentPosts(int count, List<Member> creators) {
        if (count == 0) return new ArrayList<>();

        int batchSize = 1000;
        Random random = new Random();

        for (int i = 0; i < count; i += batchSize) {
            final int startIndex = i;
            int currentBatch = Math.min(batchSize, count - i);
            String sql = "INSERT INTO content_post " +
                    "(member_id, title, description, duration, total_views, total_watch_time, url, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int j) throws SQLException {
                    int idx = startIndex + j;
                    Member creator = creators.get(random.nextInt(creators.size()));
                    int duration = random.nextInt(600) + 60; // 1~10분 사이 랜덤

                    ps.setLong(1, creator.getId());
                    ps.setString(2, "Content " + idx);
                    ps.setString(3, "Description for content " + idx);
                    ps.setInt(4, duration);
                    ps.setLong(5, 0L); // total_views 초기값
                    ps.setLong(6, 0L); // total_watch_time 초기값
                    ps.setString(7, "https://example.com/video/" + idx);
                    ps.setString(8, ContentStatus.ACTIVE.name());
                }

                @Override
                public int getBatchSize() {
                    return currentBatch;
                }
            });
        }

        return contentPostRepository.findAll();
    }

    private void createAdvertisementContentPost(ContentPost content, Advertisement ad) {
        String sql = "INSERT INTO advertisement_content_post (content_post_id, advertisement_id) VALUES (?, ?)";
        jdbcTemplate.update(sql, content.getId(), ad.getId());
    }

    private void createSettlementRates() {
        String sql = "INSERT INTO settlement_rate " +
                "(settlement_type, min_views, max_views, rate, applied_at, created_at) " +
                "VALUES (?, ?, ?, ?, NOW(), NOW())";

        // CONTENT 타입 정산율
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                switch (i) {
                    case 0: // Tier 1
                        ps.setString(1, SettlementType.CONTENT.name());
                        ps.setLong(2, 0L);
                        ps.setLong(3, 1000L);
                        ps.setBigDecimal(4, BigDecimal.valueOf(0.4));
                        break;
                    case 1: // Tier 2
                        ps.setString(1, SettlementType.CONTENT.name());
                        ps.setLong(2, 1001L);
                        ps.setLong(3, 5000L);
                        ps.setBigDecimal(4, BigDecimal.valueOf(0.5));
                        break;
                    case 2: // Tier 3
                        ps.setString(1, SettlementType.CONTENT.name());
                        ps.setLong(2, 5001L);
                        ps.setNull(3, Types.BIGINT);
                        ps.setBigDecimal(4, BigDecimal.valueOf(0.6));
                        break;
                }
            }

            @Override
            public int getBatchSize() {
                return 3;
            }
        });

        // ADVERTISEMENT 타입 정산율
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                switch (i) {
                    case 0: // Tier 1
                        ps.setString(1, SettlementType.ADVERTISEMENT.name());
                        ps.setLong(2, 0L);
                        ps.setLong(3, 1000L);
                        ps.setBigDecimal(4, BigDecimal.valueOf(0.3));
                        break;
                    case 1: // Tier 2
                        ps.setString(1, SettlementType.ADVERTISEMENT.name());
                        ps.setLong(2, 1001L);
                        ps.setLong(3, 5000L);
                        ps.setBigDecimal(4, BigDecimal.valueOf(0.4));
                        break;
                    case 2: // Tier 3
                        ps.setString(1, SettlementType.ADVERTISEMENT.name());
                        ps.setLong(2, 5001L);
                        ps.setNull(3, Types.BIGINT);
                        ps.setBigDecimal(4, BigDecimal.valueOf(0.5));
                        break;
                }
            }

            @Override
            public int getBatchSize() {
                return 3;
            }
        });
    }
}
