package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsItemProcessorTest {
    @Mock
    private ContentPostRepository contentPostRepository;

    @Mock
    private ContentStatisticsRepository contentStatisticsRepository;

    @InjectMocks
    private StatisticsItemProcessor processor;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "targetDate", targetDate);
    }

    @Test
    @DisplayName("새로운 통계 생성")
    void processNewStatistics() throws Exception {
        // given
        ContentPost contentPost = createContentPost(1L, 100L);
        CumulativeStatisticsDto dto = new CumulativeStatisticsDto(1L, 10L, 1000L, 110L);

        when(contentPostRepository.findById(1L)).thenReturn(Optional.of(contentPost));
        when(contentStatisticsRepository.findByContentPost_IdAndPeriodAndStatisticsDate(
                1L, StatisticsPeriod.DAILY, targetDate))
                .thenReturn(Optional.empty());

        // when
        ContentStatistics result = processor.process(dto);

        // then
        assertThat(result)
                .satisfies(statistics -> {
                    assertThat(statistics.getContentPost()).isEqualTo(contentPost);
                    assertThat(statistics.getStatisticsDate()).isEqualTo(targetDate);
                    assertThat(statistics.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                    assertThat(statistics.getViewCount()).isEqualTo(dto.totalViews());
                    assertThat(statistics.getWatchTime()).isEqualTo(dto.totalWatchTime());
                    assertThat(statistics.getAccumulatedViews()).isEqualTo(contentPost.getTotalViews());
                });
    }

    @Test
    @DisplayName("기존 통계 업데이트")
    void processExistingStatistics() throws Exception {
        // given
        ContentPost contentPost = createContentPost(1L, 100L);
        CumulativeStatisticsDto dto = new CumulativeStatisticsDto(1L, 10L, 1000L, 110L);
        ContentStatistics existing = createExistingStatistics(contentPost, 150L);

        when(contentPostRepository.findById(1L)).thenReturn(Optional.of(contentPost));
        when(contentStatisticsRepository.findByContentPost_IdAndPeriodAndStatisticsDate(
                1L, StatisticsPeriod.DAILY, targetDate))
                .thenReturn(Optional.of(existing));

        // when
        ContentStatistics result = processor.process(dto);

        // then
        assertThat(result)
                .satisfies(statistics -> {
                    assertThat(statistics.getViewCount()).isEqualTo(dto.totalViews());
                    assertThat(statistics.getWatchTime()).isEqualTo(dto.totalWatchTime());
                    assertThat(statistics.getAccumulatedViews()).isEqualTo(existing.getAccumulatedViews());
                });
    }

    @Test
    @DisplayName("컨텐츠를 찾을 수 없을 때 예외 발생")
    void processNotFoundContent() {
        // given
        CumulativeStatisticsDto dto = new CumulativeStatisticsDto(999L, 10L, 1000L, 110L);
        when(contentPostRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> processor.process(dto))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ContentPost not found");
    }

    private ContentPost createContentPost(Long id, Long totalViews) {
        ContentPost contentPost = ContentPost.existingBuilder()
                .title("test title")
                .url("test url")
                .totalViews(totalViews)
                .build();
        ReflectionTestUtils.setField(contentPost, "id", id);
        return contentPost;
    }

    private ContentStatistics createExistingStatistics(ContentPost contentPost, Long accumulatedViews) {
        return ContentStatistics.existingBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(5L)
                .watchTime(500L)
                .accumulatedViews(accumulatedViews)
                .build();
    }
}