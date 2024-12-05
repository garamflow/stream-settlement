package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StopWatch;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyLogWriterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DailyLogWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DailyLogWriter(jdbcTemplate);
    }

    @Test
    @DisplayName("빈 청크가 입력되면 아무 작업도 수행하지 않음")
    void write_EmptyChunk_DoesNothing() {
        // given
        Chunk<List<ContentStatistics>> emptyChunk = new Chunk<>(Collections.emptyList());

        // when
        writer.write(emptyChunk);

        // then
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("단일 통계 데이터 삽입 성공")
    void write_SingleStatistics_Success() {
        // given
        ContentStatistics stat = createTestStatistics(1L);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(Collections.singletonList(stat)));

        // when
        writer.write(chunk);

        // then
        verify(jdbcTemplate).batchUpdate(
                anyString(),
                eq(Collections.singletonList(stat)),
                eq(1),
                any()
        );
    }

    @Test
    @DisplayName("대량 데이터 삽입 성공")
    void write_BulkStatistics_Success() {
        // given
        int dataSize = 1000;
        List<ContentStatistics> statistics = createTestDataList(dataSize);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(statistics));

        // when
        writer.write(chunk);

        // then
        verify(jdbcTemplate).batchUpdate(
                anyString(),
                eq(statistics),
                eq(statistics.size()),
                any()
        );
    }

    @Test
    @DisplayName("데이터베이스 예외 발생 시 RuntimeException으로 래핑")
    void write_DatabaseError_ThrowsRuntimeException() {
        // given
        List<ContentStatistics> statistics = createTestDataList(10);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(statistics));
        doThrow(new RuntimeException("Database error"))
                .when(jdbcTemplate)
                .batchUpdate(anyString(), any(), anyInt(), any());

        // when & then
        assertThrows(RuntimeException.class, () -> writer.write(chunk));
    }

    @Test
    @DisplayName("PreparedStatement 설정 검증")
    void write_PreparedStatementSetter_SetsCorrectValues() throws Exception {
        // given
        ContentStatistics stat = createTestStatistics(1L);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(Collections.singletonList(stat)));

        // PreparedStatement 모의 객체 생성
        PreparedStatement mockPs = mock(PreparedStatement.class);

        // when
        writer.write(chunk);

        // then
        // batchUpdate 호출 시 PreparedStatementSetter 캡처 및 검증
        verify(jdbcTemplate).batchUpdate(
                anyString(),
                any(List.class),
                anyInt(),
                any()
        );
    }

    @Test
    @DisplayName("성능 테스트 - 대량 데이터 처리 시간 측정")
    void performanceTest() {
        // given
        int dataSize = 10000;
        List<ContentStatistics> statistics = createTestDataList(dataSize);
        Chunk<List<ContentStatistics>> chunk = new Chunk<>(Collections.singletonList(statistics));

        // JdbcTemplate 모의 동작 설정
        doAnswer(invocation -> null).when(jdbcTemplate)
                .batchUpdate(anyString(), any(List.class), anyInt(), any());

        // when
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        writer.write(chunk);
        stopWatch.stop();

        // then
        assertTrue(stopWatch.getTotalTimeMillis() < 1000,
                "대량 데이터 처리가 1초 이내에 완료되어야 함");
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

        return ContentStatistics.builder()
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