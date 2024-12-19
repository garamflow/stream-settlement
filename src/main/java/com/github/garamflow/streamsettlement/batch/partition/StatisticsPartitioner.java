package com.github.garamflow.streamsettlement.batch.partition;

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

/**
 * 통계 처리를 위한 데이터 파티셔닝 로직 구현
 * - 컨텐츠 ID 범위를 기준으로 데이터를 분할
 * - 요청된 gridSize에 따라 유연하게 파티션 분할
 * - 캐시 우선 조회로 DB 부하 감소
 */
@Component
@StepScope
@RequiredArgsConstructor
public class StatisticsPartitioner implements Partitioner {

    private final DailyStreamingContentCacheService dailyStreamingContentCacheService;
    private final DailyWatchedContentQuerydslRepository dailyWatchedContentQuerydslRepository;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private static final Logger log = LoggerFactory.getLogger(StatisticsPartitioner.class);

    /**
     * 파티션 생성 로직
     * - 캐시 또는 DB에서 컨텐츠 ID 범위 조회
     * - 요청된 gridSize에 따라 파티션 분할
     * - 각 파티션에 ID 범위 할당
     *
     * @param gridSize 요청된 파티션 수
     * @return 생성된 파티션 맵 (파티션명 -> 실행 컨텍스트)
     */
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

        // 파티션 크기 계산 - 최소 크기 1 보장
        long partitionSize = calculatePartitionSize(minId, maxId, gridSize);

        return createPartitions(minId, maxId, partitionSize);
    }

    /**
     * 각 파티션의 크기 계산
     * - 전체 ID 범위를 파티션 수로 균등 분할
     * - 최소 크기 1 보장
     */
    private long calculatePartitionSize(long minId, long maxId, int gridSize) {
        return Math.max((maxId - minId) / gridSize + 1, 1);
    }

    /**
     * 실제 파티션 생성
     * - ID 범위를 기준으로 파티션 분할
     * - 각 파티션에 시작 ID와 종료 ID 할당
     */
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

    /**
     * 처리할 데이터가 없는 경우의 빈 파티션 생성
     * - 시작 ID와 종료 ID를 0으로 설정한 단일 파티션 반환
     */
    private Map<String, ExecutionContext> createEmptyPartition() {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        ExecutionContext context = new ExecutionContext();
        context.putLong("startContentId", 0L);
        context.putLong("endContentId", 0L);
        partitions.put("partition0", context);
        return partitions;
    }
}