package com.github.garamflow.streamsettlement.batch.performance;

import com.github.garamflow.streamsettlement.batch.TestDataGenerator;
import com.github.garamflow.streamsettlement.batch.performance.util.PerformanceVisualizer;
import com.github.garamflow.streamsettlement.batch.writer.strategy.BulkJdbcWriter;
import com.github.garamflow.streamsettlement.batch.writer.strategy.JdbcWriter;
import com.github.garamflow.streamsettlement.batch.writer.strategy.JpaWriter;
import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentStatus;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WriterPerformanceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private JpaWriter jpaWriter;

    @Autowired
    private JdbcWriter jdbcWriter;

    @Autowired
    private BulkJdbcWriter bulkJdbcWriter;

    private static final List<Integer> DATA_SIZES = Arrays.asList(1000, 5000);
    private static final int TEST_ITERATIONS = 3;

    @BeforeEach
    void setUp() {
        try {
            // 테스트 데이터 생성 전에 기존 데이터 정리
            testDataGenerator.clearAllData();
            // 테스트 데이터 생성
            testDataGenerator.createTestData(100, 1000, 100, 100000);
        } catch (Exception e) {
            log.error("테스트 데이터 생성 중 오류 발생: ", e);
            throw e;
        }
    }

    @Test
    void compareWriterPerformance() throws IOException {
        List<List<Double>> executionTimes = new ArrayList<>();
        List<List<Double>> deadlockCounts = new ArrayList<>();

        // 각 Writer 전략별 성능 측정
        executionTimes.add(measurePerformance(jpaWriter, "JPA Writer"));
        executionTimes.add(measurePerformance(jdbcWriter, "JDBC Writer"));
        executionTimes.add(measurePerformance(bulkJdbcWriter, "JDBC Bulk Writer"));

        // 데드락 발생 횟수 측정 (실제 구현 필요)
        deadlockCounts.add(measureDeadlocks(jpaWriter, "JPA Writer"));
        deadlockCounts.add(measureDeadlocks(jdbcWriter, "JDBC Writer"));
        deadlockCounts.add(measureDeadlocks(bulkJdbcWriter, "JDBC Bulk Writer"));

        // 결과 시각화
        visualizeResults(executionTimes, deadlockCounts);
    }

    private List<Double> measurePerformance(WriterStrategy writer, String strategyName) {
        List<Double> times = new ArrayList<>();

        for (Integer size : DATA_SIZES) {
            double avgTime = 0;

            for (int i = 0; i < TEST_ITERATIONS; i++) {
                List<ContentStatistics> testData = generateTestData(size);

                long startTime = System.currentTimeMillis();
                writer.write(testData);
                long endTime = System.currentTimeMillis();

                avgTime += (endTime - startTime) / 1000.0;
            }

            avgTime /= TEST_ITERATIONS;
            times.add(avgTime);
            log.info("{} - Data Size: {}, Avg Time: {}", strategyName, size, avgTime);
        }

        return times;
    }

    private List<Double> measureDeadlocks(WriterStrategy writer, String strategyName) {
        // 데드락 측정 로직 구현
        // 실제 환경에서는 데이터베이스의 데드락 통계나 예외 발생을 모니링
        return new ArrayList<>();
    }

    private void visualizeResults(List<List<Double>> executionTimes, List<List<Double>> deadlockCounts)
            throws IOException {
        List<String> seriesNames = Arrays.asList("JPA Writer", "JDBC Writer", "JDBC Bulk Writer");

        // 실행 시간 그래프
        PerformanceVisualizer.createPerformanceChart(
                "Writer 성능 비교",
                DATA_SIZES,
                executionTimes,
                seriesNames,
                "데이터 크기",
                "실행 시간 (초)",
                "performance-results/writer-performance.png"
        );

        // 데드락 발생 횟수 그래프
        PerformanceVisualizer.createPerformanceChart(
                "Writer 데드락 발생 비교",
                DATA_SIZES,
                deadlockCounts,
                seriesNames,
                "데이터 크기",
                "데드락 발생 횟수",
                "performance-results/writer-deadlocks.png"
        );
    }

    private List<ContentStatistics> generateTestData(int size) {
        List<ContentStatistics> statistics = new ArrayList<>();

        // 현재 시간을 밀리초로 가져와서 고유한 값 생성에 사용
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < size; i++) {
            // Member 생성 - 고유한 이메일 주소 사용
            Member member = Member.builder()
                    .email(String.format("test%d_%d@test.com", i, timestamp))
                    .role(Role.CREATOR)
                    .username("Test User " + i)
                    .build();

            // ContentPost 생성
            ContentPost contentPost = ContentPost.builder()
                    .member(member)
                    .title("Test Content " + i)
                    .url("http://test.com/content/" + timestamp + "/" + i)
                    .status(ContentStatus.ACTIVE)
                    .build();

            // 엔티티 저장
            entityManager.persist(member);
            entityManager.persist(contentPost);

            // ContentStatistics 생성
            ContentStatistics stat = ContentStatistics.builder()
                    .contentPost(contentPost)
                    .period(StatisticsPeriod.DAILY)
                    .statisticsDate(LocalDate.now())
                    .viewCount(0L)
                    .watchTime(0L)
                    .accumulatedViews(0L)
                    .build();

            statistics.add(stat);
        }

        // 변경사항을 즉시 데이터베이스에 반영
        entityManager.flush();

        return statistics;
    }
}

public interface WriterStrategy {
    void write(List<ContentStatistics> statistics);
}