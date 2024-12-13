package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class SettlementPartitioner implements Partitioner {

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;
    private final ContentStatisticsRepository contentStatisticsRepository;

    @Override
    @NonNull
    public Map<String, ExecutionContext> partition(int gridSize) {
        LocalDate date = LocalDate.parse(targetDate);
        long minId = contentStatisticsRepository.findMinIdByStatisticsDate(date);
        long maxId = contentStatisticsRepository.findMaxIdByStatisticsDate(date);

        if (minId == 0 || maxId == 0) {
            log.warn("No statistics found for date: {}", targetDate);
            return createEmptyPartition();
        }

        long partitionSize = (maxId - minId) / gridSize + 1;
        return createPartitions(minId, maxId, partitionSize);
    }

    private Map<String, ExecutionContext> createPartitions(long minId, long maxId, long partitionSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        int partitionNumber = 1;
        long startId = minId;
        long endId = startId + partitionSize - 1;

        while (startId <= maxId) {
            endId = Math.min(endId, maxId);

            ExecutionContext context = new ExecutionContext();
            context.putLong("startStatisticsId", startId);
            context.putLong("endStatisticsId", endId);
            context.putString("targetDate", targetDate);

            partitions.put("settlement-partition" + partitionNumber, context);

            startId += partitionSize;
            endId += partitionSize;
            partitionNumber++;
        }

        return partitions;
    }

    private Map<String, ExecutionContext> createEmptyPartition() {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        ExecutionContext context = new ExecutionContext();
        context.putLong("startStatisticsId", 0L);
        context.putLong("endStatisticsId", 0L);
        context.putString("targetDate", targetDate);
        partitions.put("settlement-partition0", context);
        return partitions;
    }
} 