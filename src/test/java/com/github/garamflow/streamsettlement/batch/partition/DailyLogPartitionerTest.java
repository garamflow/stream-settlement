package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQueryRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyLogPartitionerTest {
    @Mock
    private DailyStreamingContentCacheService dailyStreamingContentCacheService;
    @Mock
    private DailyWatchedContentQueryRepository dailyWatchedContentQueryRepository;
    @Mock
    private BatchProperties batchProperties;

    @InjectMocks
    private DailyLogPartitioner partitioner;

    private LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(partitioner, "targetDate", targetDate);
    }

    @Test
    @DisplayName("캐시된 컨텐츠가 있을 때 파티션 생성")
    void createPartitionsWithCachedContent() {
        // given
        Set<Long> contentIds = Set.of(1L, 5L, 10L);
        when(dailyStreamingContentCacheService.getContentIdsByDate(targetDate))
                .thenReturn(contentIds);

        BatchProperties.Partition partition = mock(BatchProperties.Partition.class);
        when(partition.getSmallDataSize()).thenReturn(5L);
        when(partition.getSmallGridSize()).thenReturn(2);
        when(batchProperties.getPartition()).thenReturn(partition);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get("partition1"))
                .extracting(context -> context.getLong("startContentId"),
                        context -> context.getLong("endContentId"))
                .containsExactly(1L, 5L);
    }

    @Test
    @DisplayName("캐시된 컨텐츠가 없을 때 DB에서 조회하여 파티션 생성")
    void createPartitionsFromDatabase() {
        // given
        when(dailyStreamingContentCacheService.getContentIdsByDate(targetDate))
                .thenReturn(Collections.emptySet());
        when(dailyWatchedContentQueryRepository.findMinIdByWatchedDate(targetDate))
                .thenReturn(1L);
        when(dailyWatchedContentQueryRepository.findMaxIdByWatchedDate(targetDate))
                .thenReturn(10L);

        BatchProperties.Partition partition = mock(BatchProperties.Partition.class);
        when(partition.getSmallDataSize()).thenReturn(20L);
        when(partition.getSmallGridSize()).thenReturn(2);
        when(batchProperties.getPartition()).thenReturn(partition);

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
} 