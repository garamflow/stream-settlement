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

        // Validate targetDate parameter
        if (targetDate == null || targetDate.isEmpty()) {
            throw new IllegalArgumentException("Job parameter 'targetDate' is required but was not provided.");
        }

        log.debug("Partitioning for target date: {}", targetDate);

        try {
            // Count records for the given targetDate
            String countSql = """
                    SELECT COUNT(*)
                    FROM daily_member_view_log
                    WHERE log_date = ?
                    """;
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, targetDate);

            if (count == null || count == 0) {
                log.warn("No data found for target date: {}", targetDate);
                return partitions; // Return empty partitions
            }

            // Retrieve min and max IDs for partitioning
            String sql = """
                    SELECT MIN(content_post_id) as min_id,
                           MAX(content_post_id) as max_id
                    FROM daily_member_view_log
                    WHERE log_date = ?
                    """;
            Map<String, Object> minMaxIds = jdbcTemplate.queryForMap(sql, targetDate);

            if (minMaxIds.isEmpty()) {
                throw new IllegalStateException("Failed to retrieve min and max IDs for target date: " + targetDate);
            }

            long min = ((Number) minMaxIds.get("min_id")).longValue();
            long max = ((Number) minMaxIds.get("max_id")).longValue();

            if (min > max) {
                throw new IllegalStateException("Invalid range for partitioning. min_id: " + min + ", max_id: " + max);
            }

            // Calculate partition size
            long totalRecords = max - min + 1;
            long targetSize = Math.max(1, (long) Math.ceil((double) totalRecords / gridSize));
            long start = min;

            // Create partitions
            for (int i = 0; i < gridSize && start <= max; i++) {
                long end = Math.min(start + targetSize - 1, max);

                ExecutionContext context = new ExecutionContext();
                context.putLong("minId", start);
                context.putLong("maxId", end);
                partitions.put("partition" + i, context);

                log.debug("Created partition {}: [{} - {}]", i, start, end);
                start = end + 1;
            }
        } catch (Exception e) {
            log.error("Error occurred while partitioning for target date: {}", targetDate, e);
            throw e;
        }

        return partitions;
    }
}
