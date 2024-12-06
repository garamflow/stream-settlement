package com.github.garamflow.streamsettlement.batch.partition;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogPartitioner implements Partitioner {
    private final JdbcTemplate jdbcTemplate;
    private static final Logger log = LoggerFactory.getLogger(DailyLogPartitioner.class);

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    @NonNull
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();

        try {
            String sql = """
                    SELECT COALESCE(MIN(daily_member_view_log_id), 0) as min_id,
                           COALESCE(MAX(daily_member_view_log_id), 0) as max_id,
                           COUNT(*) as total_count
                    FROM daily_member_view_log
                    WHERE log_date = ?
                    AND last_viewed_position > 0
                    """;

            Map<String, Object> result = jdbcTemplate.queryForMap(sql, targetDate);
            long minId = ((Number) result.get("min_id")).longValue();
            long maxId = ((Number) result.get("max_id")).longValue();
            long totalCount = ((Number) result.get("total_count")).longValue();

            int actualGridSize = calculateGridSize(totalCount);
            long targetSize = (maxId - minId + actualGridSize) / actualGridSize;

            for (int i = 0; i < actualGridSize; i++) {
                ExecutionContext context = new ExecutionContext();
                long start = minId + (i * targetSize);
                long end = i == actualGridSize - 1 ? maxId : start + targetSize - 1;

                if (start > end) continue;

                context.putLong("minId", start);
                context.putLong("maxId", end);
                context.putString("targetDate", targetDate);
                context.putString("partitionId", String.valueOf(i));

                partitions.put("partition" + i, context);
                log.info("Created partition{}: minId={}, maxId={}", i, start, end);
            }
        } catch (Exception e) {
            log.warn("Error during partitioning for date: {}", targetDate, e);
            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", 0L);
            context.putLong("maxId", 0L);
            context.putString("targetDate", targetDate);
            partitions.put("partition0", context);
        }

        return partitions;
    }

    private int calculateGridSize(long totalCount) {
        if (totalCount < 100) return 1;
        if (totalCount < 1000) return 2;
        if (totalCount < 10000) return 4;
        return 8;
    }
}
