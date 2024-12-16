package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQuerydslRepository;
import com.github.garamflow.streamsettlement.service.cache.DailyStreamingContentCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@StepScope
@RequiredArgsConstructor
public class StatisticsPartitioner implements Partitioner {

    private final DailyStreamingContentCacheService dailyStreamingContentCacheService;
    private final DailyWatchedContentQuerydslRepository dailyWatchedContentQuerydslRepository;
    private final BatchProperties batchProperties;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private static final Logger log = LoggerFactory.getLogger(StatisticsPartitioner.class);

    @Override
    @NonNull
    public Map<String, ExecutionContext> partition(int gridSize) {
        Set<Long> contentIds = dailyStreamingContentCacheService.getContentIdsByDate(targetDate);

        Long minId;
        Long maxId;

        if (contentIds.isEmpty()) {
            // contentIds 가 비어있으면 DB 에서 min/max 조회
            minId = dailyWatchedContentQuerydslRepository.findMinIdByWatchedDate(targetDate);
            maxId = dailyWatchedContentQuerydslRepository.findMaxIdByWatchedDate(targetDate);

            if (minId == null || maxId == null) {
                log.warn("No streamed content found for date: {}", targetDate);
                return createEmptyPartition();
            }
        } else {
            // contentIds 기반 min/max
            minId = Collections.min(contentIds);
            maxId = Collections.max(contentIds);
        }

        int dataSize = (int) (maxId - minId + 1);
        int adjustedGridSize = determineGridSize(dataSize);
        long partitionSize = calculatePartitionSize(minId, maxId, adjustedGridSize);

        return createPartitions(minId, maxId, partitionSize);
    }

    private int determineGridSize(int dataSize) {
        BatchProperties.Partition partition = batchProperties.getPartition();
        // 데이터량에 따라 파티션 수 결정
        if (dataSize < partition.getSmallDataSize()) {
            return partition.getSmallGridSize();
        } else if (dataSize < partition.getMediumDataSize()) {
            return partition.getMediumGridSize();
        } else if (dataSize < partition.getLargeDataSize()) {
            return partition.getLargeGridSize();
        }
        return partition.getExtraLargeGridSize();
    }

    private long calculatePartitionSize(long minId, long maxId, int gridSize) {
        // 파티션 수(gridSize)에 따라 각 파티션 크기 동적 결정
        return (maxId - minId) / gridSize + 1;
    }

    private Map<String, ExecutionContext> createPartitions(long minId, long maxId, long partitionSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        int partitionNumber = 1;

        for (long startId = minId; startId <= maxId; startId += partitionSize) {
            long endId = Math.min(startId + partitionSize - 1, maxId);

            ExecutionContext context = new ExecutionContext();
            context.putLong("startContentId", startId);
            context.putLong("endContentId", endId);
            partitions.put("partition" + partitionNumber++, context);
        }

        return partitions;
    }

    private Map<String, ExecutionContext> createEmptyPartition() {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        ExecutionContext context = new ExecutionContext();
        context.putLong("startContentId", 0L);
        context.putLong("endContentId", 0L);
        partitions.put("partition0", context);
        return partitions;
    }
}