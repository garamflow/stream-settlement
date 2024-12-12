package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentStatus;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StopWatch;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyLogWriterTest {

    @Mock
    private ContentStatisticsRepository contentStatisticsRepository;

    @InjectMocks
    private DailyLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DailyLogWriter(contentStatisticsRepository);
    }

    @Test
    @DisplayName("빈 청크가 입력되면 빈 리스트로 처리")
    void write_EmptyChunk_ProcessesEmptyList() throws Exception {
        // given
        Chunk<List<ContentStatistics>> emptyChunk = new Chunk<>(Collections.emptyList());

        // when
        writer.write(emptyChunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(argThat(List::isEmpty));
    }

    @Test
    @DisplayName("단일 통계 데이터 삽입 성공")
    void write_SingleStatistics_Success() throws Exception {
        // given
        ContentStatistics stat = createTestStatistics(1L);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(Collections.singletonList(stat)));

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(argThat(list ->
                list.size() == 1 && list.get(0).equals(stat)
        ));
    }

    @Test
    @DisplayName("대량 데이터 삽입 성공")
    void write_BulkStatistics_Success() throws Exception {
        // given
        int dataSize = 1000000;
        List<ContentStatistics> statistics = createTestDataList(dataSize);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(statistics));

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(argThat(list ->
                list.size() == dataSize &&
                        list.stream().sorted(Comparator.comparing(stat -> stat.getContentPost().getId()))
                                .toList().equals(statistics)
        ));
    }

    @Test
    @DisplayName("데이터베이스 예외 발생 시 RuntimeException으로 래핑")
    void write_DatabaseError_ThrowsRuntimeException() {
        // given
        List<ContentStatistics> statistics = createTestDataList(10);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(statistics));
        doThrow(new RuntimeException("Database error"))
                .when(contentStatisticsRepository)
                .bulkInsertStatistics(any());

        // when & then
        assertThrows(RuntimeException.class, () -> writer.write(chunk));
    }

    @Test
    @DisplayName("성능 테스트 - 대량 데이터 처리 시간 측정")
    void performanceTest() throws Exception {
        // given
        int dataSize = 10000;
        List<ContentStatistics> statistics = createTestDataList(dataSize);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(statistics));

        // when
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        writer.write(chunk);
        stopWatch.stop();

        // then
        assertTrue(stopWatch.getTotalTimeMillis() < 1000,
                "대량 데이터 처리가 1초 이내에 완료되어야 함");
    }

    @Test
    @DisplayName("통계 데이터 일괄 저장")
    void writeStatistics() throws Exception {
        // given
        ContentPost contentPost = ContentPost.builder()
                .title("test title")
                .url("test url")
                .totalViews(100L)
                .build();
        ReflectionTestUtils.setField(contentPost, "id", 1L);

        List<ContentStatistics> statistics = List.of(
                ContentStatistics.customBuilder()
                        .contentPost(contentPost)
                        .statisticsDate(LocalDate.now())
                        .period(StatisticsPeriod.DAILY)
                        .viewCount(1L)
                        .watchTime(100L)
                        .accumulatedViews(101L)
                        .build()
        );

        Chunk<List<ContentStatistics>> chunk = new Chunk<>(List.of(statistics));

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(statistics);
    }

    @Test
    @DisplayName("여러 청크의 통계 데이터 일괄 저장")
    void writeMultipleStatistics() throws Exception {
        // given
        ContentPost contentPost1 = ContentPost.builder()
                .title("test title 1")
                .url("test url 1")
                .totalViews(100L)
                .build();
        ReflectionTestUtils.setField(contentPost1, "id", 1L);

        ContentPost contentPost2 = ContentPost.builder()
                .title("test title 2")
                .url("test url 2")
                .totalViews(200L)
                .build();
        ReflectionTestUtils.setField(contentPost2, "id", 2L);

        List<ContentStatistics> statistics1 = List.of(
                ContentStatistics.customBuilder()
                        .contentPost(contentPost1)
                        .statisticsDate(LocalDate.now())
                        .period(StatisticsPeriod.DAILY)
                        .viewCount(1L)
                        .watchTime(100L)
                        .accumulatedViews(101L)
                        .build()
        );

        List<ContentStatistics> statistics2 = List.of(
                ContentStatistics.customBuilder()
                        .contentPost(contentPost2)
                        .statisticsDate(LocalDate.now())
                        .period(StatisticsPeriod.DAILY)
                        .viewCount(1L)
                        .watchTime(200L)
                        .accumulatedViews(201L)
                        .build()
        );

        Chunk<List<ContentStatistics>> chunk = new Chunk<>(List.of(statistics1, statistics2));

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(
                argThat(list ->
                        list.size() == 2 &&
                                list.containsAll(statistics1) &&
                                list.containsAll(statistics2)
                )
        );
    }

    private ContentStatistics createTestStatistics(Long id) {
        // Member 객체 생성
        Member member = Member.builder()
                .email(String.format("test%d_%d@test.com", id, System.currentTimeMillis()))
                .role(Role.CREATOR)
                .username("Test User " + id)
                .build();

        // ContentPost 객체를 빌더를 사용하여 생성
        ContentPost contentPost = ContentPost.builder()
                .member(member)
                .title("Test Content " + id)
                .url("http://test.com/content/" + id)
                .status(ContentStatus.ACTIVE)
                .build();

        ReflectionTestUtils.setField(contentPost, "id", id);

        return ContentStatistics.customBuilder()
                .contentPost(contentPost)
                .statisticsDate(LocalDate.now())
                .period(StatisticsPeriod.DAILY)
                .viewCount(1L)
                .watchTime(100L)
                .accumulatedViews(1000L)
                .build();
    }

    private List<ContentStatistics> createTestDataList(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> createTestStatistics((long) i))
                .collect(Collectors.toList());
    }
}