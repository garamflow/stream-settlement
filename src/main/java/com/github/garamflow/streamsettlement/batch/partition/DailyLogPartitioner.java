package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQueryRepository;
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
public class DailyLogPartitioner implements Partitioner {
    private final DailyStreamingContentCacheService dailyStreamingContentCacheService;
    private final DailyWatchedContentQueryRepository dailyWatchedContentQueryRepository;
    private final BatchProperties batchProperties;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private static final Logger log = LoggerFactory.getLogger(DailyLogPartitioner.class);

    @Override
    @NonNull
    public Map<String, ExecutionContext> partition(int gridSize) {
        Set<Long> contentIds = dailyStreamingContentCacheService.getContentIdsByDate(targetDate);

        if (contentIds.isEmpty()) {
            Long minId = dailyWatchedContentQueryRepository.findMinIdByWatchedDate(targetDate);
            Long maxId = dailyWatchedContentQueryRepository.findMaxIdByWatchedDate(targetDate);

            if (minId == null || maxId == null) {
                log.warn("No streamed content found for date: {}", targetDate);
                return createEmptyPartition();
            }

            int adjustedGridSize = determineGridSize((int) (maxId - minId + 1));
            long partitionSize = (maxId - minId) / adjustedGridSize + 1;
            return createPartitions(minId, maxId, partitionSize);
        }

        Long minId = Collections.min(contentIds);
        Long maxId = Collections.max(contentIds);
        int adjustedGridSize = determineGridSize(contentIds.size());
        long partitionSize = (maxId - minId) / adjustedGridSize + 1;

        return createPartitions(minId, maxId, partitionSize);
    }

    private int determineGridSize(int dataSize) {
        BatchProperties.Partition partition = batchProperties.getPartition();
        if (dataSize < partition.getSmallDataSize()) {
            return partition.getSmallGridSize();
        } else if (dataSize < partition.getMediumDataSize()) {
            return partition.getMediumGridSize();
        } else if (dataSize < partition.getLargeDataSize()) {
            return partition.getLargeGridSize();
        }
        return partition.getExtraLargeGridSize();
    }

    private Map<String, ExecutionContext> createPartitions(long minId, long maxId, long partitionSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        int partitionNumber = 1;
        long currentPartitionStartId = minId;
        long currentPartitionEndId = currentPartitionStartId + partitionSize - 1;

        while (currentPartitionStartId <= maxId) {
            currentPartitionEndId = Math.min(currentPartitionEndId, maxId);

            ExecutionContext context = new ExecutionContext();
            context.putLong("startContentId", currentPartitionStartId);
            context.putLong("endContentId", currentPartitionEndId);

            partitions.put(String.format("partition%d", partitionNumber), context);
            log.info("Created partition{}: startContentId={}, endContentId={}",
                    partitionNumber, currentPartitionStartId, currentPartitionEndId);

            currentPartitionStartId += partitionSize;
            currentPartitionEndId += partitionSize;
            partitionNumber++;
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
