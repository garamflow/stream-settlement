package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatisticsItemWriterTest {

    @Mock
    private ContentStatisticsRepository contentStatisticsRepository;

    @InjectMocks
    private StatisticsItemWriter writer;

    @Test
    @DisplayName("빈 청크 입력시 빈 리스트로 처리")
    void writeEmptyChunk() throws Exception {
        // given
        Chunk<ContentStatistics> chunk = new Chunk<>();

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(List.of());
    }

    @Test
    @DisplayName("단일 통계 데이터 처리")
    void writeSingleStatistics() throws Exception {
        // given
        ContentStatistics stat = createContentStatistics(1L);
        Chunk<ContentStatistics> chunk = new Chunk<>(List.of(stat));

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(List.of(stat));
    }

    @Test
    @DisplayName("다중 통계 데이터 처리")
    void writeMultipleStatistics() throws Exception {
        // given
        List<ContentStatistics> stats = List.of(
                createContentStatistics(1L),
                createContentStatistics(2L)
        );
        Chunk<ContentStatistics> chunk = new Chunk<>(stats);

        // when
        writer.write(chunk);

        // then
        verify(contentStatisticsRepository).bulkInsertStatistics(stats);
    }

    @Test
    @DisplayName("저장소 예외 발생시 RuntimeException 발생")
    void throwsExceptionWhenRepositoryFails() {
        // given
        ContentStatistics stat = createContentStatistics(1L);
        Chunk<ContentStatistics> chunk = new Chunk<>(List.of(stat));

        doThrow(new RuntimeException("DB 에러"))
                .when(contentStatisticsRepository)
                .bulkInsertStatistics(any());

        // when & then
        assertThatThrownBy(() -> writer.write(chunk))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 에러");
    }

    private ContentStatistics createContentStatistics(Long id) {
        ContentPost contentPost = ContentPost.existingBuilder()
                .title("테스트 콘텐츠 " + id)
                .url("http://test.com/" + id)
                .build();
        ReflectionTestUtils.setField(contentPost, "id", id);

        return ContentStatistics.existingBuilder()
                .contentPost(contentPost)
                .statisticsDate(LocalDate.now())
                .period(StatisticsPeriod.DAILY)
                .viewCount(100L)
                .watchTime(1000L)
                .accumulatedViews(500L)
                .build();
    }
}