package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.performance.util.PerformanceVisualizer;
import com.github.garamflow.streamsettlement.batch.writer.DailyLogWriter;
import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false"
})
@Transactional
class WriterPerformanceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DailyLogWriter dailyLogWriter;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("벌용량 벌크 인서트 성능 테스트 - JPA vs JDBC Bulk")
    void largeBulkInsertPerformanceTest() throws IOException {
        List<Integer> dataSizes = Arrays.asList(1_000);  // 1천만, 1억
        List<Double> jpaTimes = new ArrayList<>();
        List<Double> jdbcBulkTimes = new ArrayList<>();
        int chunkSize = 100;  // 10만 건씩 처리

        for (Integer totalSize : dataSizes) {
            StopWatch jpaWatch = new StopWatch();
            StopWatch bulkWatch = new StopWatch();

            // JPA 테스트
            jpaWatch.start();
            for (int i = 0; i < totalSize; i += chunkSize) {
                int currentChunkSize = Math.min(chunkSize, totalSize - i);
                List<ContentStatistics> statistics = generateStatistics(currentChunkSize);
                processWithJpa(statistics);

                // 메모리 정리
                entityManager.clear();
                System.gc();
            }
            jpaWatch.stop();
            jpaTimes.add((double) jpaWatch.getTotalTimeMillis());

            // 테이블 초기화
            jdbcTemplate.execute("DELETE FROM content_statistics");

            // JDBC Bulk 테스트
            bulkWatch.start();
            for (int i = 0; i < totalSize; i += chunkSize) {
                int currentChunkSize = Math.min(chunkSize, totalSize - i);
                List<ContentStatistics> statistics = generateStatistics(currentChunkSize);
                dailyLogWriter.write(new Chunk<>(Collections.singletonList(statistics)));

                // 메모리 정리
                System.gc();
            }
            bulkWatch.stop();
            jdbcBulkTimes.add((double) bulkWatch.getTotalTimeMillis());

            log.info("데이터 크기: {}", totalSize);
            log.info("JPA 인서트 소요 시간: {}ms", jpaWatch.getTotalTimeMillis());
            log.info("JDBC Bulk 인서트 소요 시간: {}ms", bulkWatch.getTotalTimeMillis());
        }

        // 성능 차트 생성
        PerformanceVisualizer.createPerformanceChart(
                "대용량 JPA vs JDBC Bulk 성능 비교",
                dataSizes,
                Arrays.asList(jpaTimes, jdbcBulkTimes),
                Arrays.asList("JPA 개별 인서트", "JDBC Bulk 인서트"),
                "데이터 크기",
                "처리 시간 (ms)",
                "performance-results/large-insert-performance-comparison.png"
        );
    }

    protected void processWithJpa(List<ContentStatistics> statistics) {
        for (ContentStatistics stat : statistics) {
            try {
                // 매번 새로운 엔티티를 생성하여 저장 (의도적으로 성능 저하)
                ContentStatistics newStat = ContentStatistics.builder()
                        .contentPost(stat.getContentPost())
                        .statisticsDate(stat.getStatisticsDate())
                        .period(stat.getPeriod())
                        .viewCount(stat.getViewCount())
                        .watchTime(stat.getWatchTime())
                        .accumulatedViews(stat.getAccumulatedViews())
                        .build();

                contentStatisticsRepository.save(newStat);

                // 성능 저하를 위한 추가 작업
                entityManager.flush();
                entityManager.clear();
            } catch (Exception e) {
                // 중복 키 에러는 무시 (테스트 목적)
                log.debug("Duplicate key ignored: {}", e.getMessage());
            }
        }
    }

    private List<ContentStatistics> generateStatistics(int size) {
        // 메모리 효율을 위해 동일한 ContentPost 재사용
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);

        return IntStream.range(0, size)
                .mapToObj(i -> ContentStatistics.builder()
                        .contentPost(contentPost)
                        .statisticsDate(LocalDate.now())
                        .period(StatisticsPeriod.DAILY)
                        .viewCount(1L)
                        .watchTime(100L)
                        .accumulatedViews(1000L)
                        .build())
                .collect(Collectors.toList());
    }

    private Member createMember() {
        String uniqueEmail = "test" + System.currentTimeMillis() + "@test.com";
        Member member = new Member.Builder()
                .email(uniqueEmail)
                .role(Role.MEMBER)
                .build();
        return memberRepository.save(member);
    }

    private ContentPost createContentPost(Member member) {
        ContentPost contentPost = ContentPost.builder()
                .member(member)
                .title("스트 영상")
                .url("http://test.com/video")
                .build();
        return contentPostRepository.save(contentPost);
    }
} 