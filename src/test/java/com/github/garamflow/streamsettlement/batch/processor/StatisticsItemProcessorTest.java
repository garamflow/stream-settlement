package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
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
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsItemProcessorTest {
    @Mock
    private ContentPostRepository contentPostRepository;

    @InjectMocks
    private StatisticsItemProcessor processor;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);
    private final ContentPost contentPost1 = createContentPost(1L, 100L);
    private final ContentPost contentPost2 = createContentPost(2L, 200L);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "targetDate", targetDate);
        
        // 명시적으로 리스트 생성
        List<ContentPost> contentPosts = Arrays.asList(contentPost1, contentPost2);
        when(contentPostRepository.findAll()).thenReturn(contentPosts);
        
        // 캐시 초기화
        processor.init();
    }

    @Test
    @DisplayName("캐시된 컨텐츠로 통계 생성")
    void processWithCachedContent() throws Exception {
        // given
        CumulativeStatisticsDto dto = new CumulativeStatisticsDto(1L, 10L, 1000L, 110L);

        // when
        ContentStatistics result = processor.process(dto);

        // then
        assertThat(result)
                .isNotNull()
                .satisfies(statistics -> {
                    assertThat(statistics.getContentPost()).isEqualTo(contentPost1);
                    assertThat(statistics.getStatisticsDate()).isEqualTo(targetDate);
                    assertThat(statistics.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                    assertThat(statistics.getViewCount()).isEqualTo(dto.totalViews());
                    assertThat(statistics.getWatchTime()).isEqualTo(dto.totalWatchTime());
                    assertThat(statistics.getAccumulatedViews()).isEqualTo(contentPost1.getTotalViews());
                });
    }

    @Test
    @DisplayName("캐시에 없는 컨텐츠 처리시 예외 발생")
    void processNotCachedContent() {
        // given
        CumulativeStatisticsDto dto = new CumulativeStatisticsDto(999L, 10L, 1000L, 110L);
        when(contentPostRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> processor.process(dto))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ContentPost not found in cache or database for id: 999");
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
}