package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQuerydslRepository;
import com.github.garamflow.streamsettlement.service.cache.DailyStreamingContentCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsPartitionerTest {
    @Mock
    private DailyStreamingContentCacheService dailyStreamingContentCacheService;
    @Mock
    private DailyWatchedContentQuerydslRepository dailyWatchedContentQuerydslRepository;
    @Mock
    private BatchProperties.Partition partitionProperties;

    @InjectMocks
    private StatisticsPartitioner partitioner;

    private LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(partitioner, "targetDate", targetDate);
    }

    @Test
    @DisplayName("소규모 데이터에 대한 캐시 기반 파티션 생성")
    void createSmallPartitionsWithCache() {
        // given
        Set<Long> contentIds = Set.of(1L, 5L, 10L);
        when(dailyStreamingContentCacheService.getContentIdsByDate(targetDate))
                .thenReturn(contentIds);
        when(partitionProperties.getSmallDataSize()).thenReturn(100L);
        when(partitionProperties.getSmallGridSize()).thenReturn(2);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get("partition1"))
                .extracting(context -> context.getLong("startContentId"),
                        context -> context.getLong("endContentId"))
                .containsExactly(1L, 5L);
        assertThat(result.get("partition2"))
                .extracting(context -> context.getLong("startContentId"),
                        context -> context.getLong("endContentId"))
                .containsExactly(6L, 10L);
    }

    @Test
    @DisplayName("DB 조회 기반 파티션 생성")
    void createPartitionsFromDatabase() {
        // given
        when(dailyStreamingContentCacheService.getContentIdsByDate(targetDate))
                .thenReturn(Collections.emptySet());
        when(dailyWatchedContentQuerydslRepository.findMinIdByWatchedDate(targetDate))
                .thenReturn(1L);
        when(dailyWatchedContentQuerydslRepository.findMaxIdByWatchedDate(targetDate))
                .thenReturn(100L);
        when(partitionProperties.getSmallDataSize()).thenReturn(1000L);
        when(partitionProperties.getSmallGridSize()).thenReturn(4);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result).hasSize(4);
        assertThat(result.get("partition1"))
                .extracting(context -> context.getLong("startContentId"),
                        context -> context.getLong("endContentId"))
                .containsExactly(1L, 25L);
    }

    @Test
    @DisplayName("데이터가 없을 때 빈 파티션 생성")
    void createEmptyPartition() {
        // given
        when(dailyStreamingContentCacheService.getContentIdsByDate(targetDate))
                .thenReturn(Collections.emptySet());
        when(dailyWatchedContentQuerydslRepository.findMinIdByWatchedDate(targetDate))
                .thenReturn(null);
        when(dailyWatchedContentQuerydslRepository.findMaxIdByWatchedDate(targetDate))
                .thenReturn(null);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(2);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get("partition0"))
                .extracting(context -> context.getLong("startContentId"),
                        context -> context.getLong("endContentId"))
                .containsExactly(0L, 0L);
    }

    @Test
    @DisplayName("대규모 데이터에 대한 파티션 크기 조정")
    void adjustPartitionSizeForLargeData() {
        // given
        when(dailyStreamingContentCacheService.getContentIdsByDate(targetDate))
                .thenReturn(Collections.emptySet());
        when(dailyWatchedContentQuerydslRepository.findMinIdByWatchedDate(targetDate))
                .thenReturn(1L);
        when(dailyWatchedContentQuerydslRepository.findMaxIdByWatchedDate(targetDate))
                .thenReturn(5000L);
        when(partitionProperties.getMediumDataSize()).thenReturn(10000L);
        when(partitionProperties.getMediumGridSize()).thenReturn(8);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(8);

        // then
        assertThat(result).hasSize(8);
        assertThat(result.get("partition1"))
                .extracting(context -> context.getLong("startContentId"))
                .isEqualTo(1L);
        assertThat(result.get("partition8"))
                .extracting(context -> context.getLong("endContentId"))
                .isEqualTo(5000L);
    }
} 