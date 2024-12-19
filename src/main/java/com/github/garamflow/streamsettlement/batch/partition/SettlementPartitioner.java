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

/**
 * 정산 처리를 위한 데이터 파티셔닝 로직 구현
 * - 통계 데이터 ID 범위를 기준으로 파티션 분할
 * - 각 파티션별로 독립적인 정산 처리 수행
 */
@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class SettlementPartitioner implements Partitioner {

    // 처리 대상 날짜 (Job Parameter)
    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private final ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;

    /**
     * 파티션 생성 로직
     * - 해당 날짜의 통계 데이터 ID 범위 조회
     * - ID 범위를 기준으로 파티션 분할
     * 
     * @param gridSize 요청된 파티션 수
     * @return 생성된 파티션 맵 (파티션명 -> 실행 컨텍스트)
     */
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
     * - 전체 ID 범위를 파티션 수로 균등 분할
     * - 최소 크기 1 보장
     */
    private long calculatePartitionSize(long minId, long maxId, int gridSize) {
        return Math.max((maxId - minId) / gridSize + 1, 1);
    }

    /**
     * Partition 생성
     * - ID 범위를 기준으로 파티션 분할
     * - 각 파티션에 시작/종료 ID와 처리 날짜 할당
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
            context.putString("targetDate", targetDate.toString());

            partitions.put("settlement-partition" + partitionNumber, context);

            currentStartId += partitionSize;
            partitionNumber++;
        }

        return partitions;
    }

    /**
     * 빈 Partition 생성
     * - 처리할 데이터가 없는 경우 사용
     * - ID를 0으로 설정한 단일 파티션 반환
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