package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsQuerydslRepository;
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
    private LocalDate targetDate; // 날짜를 LocalDate로 바로 매핑

    private final ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;

    @Override
    @NonNull
    public Map<String, ExecutionContext> partition(int gridSize) {
        // 데이터베이스에서 최소 및 최대 ID 조회
        long minId = contentStatisticsQuerydslRepository.findMinIdByStatisticsDate(targetDate);
        long maxId = contentStatisticsQuerydslRepository.findMaxIdByStatisticsDate(targetDate);

        // 데이터가 없는 경우 빈 파티션 생성
        if (minId == 0 || maxId == 0) {
            log.warn("No statistics found for date: {}", targetDate);
            return createEmptyPartition();
        }

        // Partition 크기 계산
        long partitionSize = calculatePartitionSize(minId, maxId, gridSize);

        // Partition 생성
        return createPartitions(minId, maxId, partitionSize);
    }

    /**
     * Partition 크기 계산
     */
    private long calculatePartitionSize(long minId, long maxId, int gridSize) {
        return Math.max((maxId - minId) / gridSize + 1, 1); // 최소 1 이상 보장
    }

    /**
     * Partition 생성
     */
    private Map<String, ExecutionContext> createPartitions(long minId, long maxId, long partitionSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        int partitionNumber = 1;
        long currentStartId = minId;

        while (currentStartId <= maxId) {
            long currentEndId = Math.min(currentStartId + partitionSize - 1, maxId);

            ExecutionContext context = new ExecutionContext();
            context.putLong("startStatisticsId", currentStartId);
            context.putLong("endStatisticsId", currentEndId);
            context.putString("targetDate", targetDate.toString()); // 추가 정보로 날짜 포함

            partitions.put("settlement-partition" + partitionNumber, context);

            currentStartId += partitionSize;
            partitionNumber++;
        }

        return partitions;
    }

    /**
     * 빈 Partition 생성
     */
    private Map<String, ExecutionContext> createEmptyPartition() {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        ExecutionContext context = new ExecutionContext();
        context.putLong("startStatisticsId", 0L);
        context.putLong("endStatisticsId", 0L);
        context.putString("targetDate", targetDate != null ? targetDate.toString() : "unknown");
        partitions.put("settlement-partition0", context);
        return partitions;
    }
}