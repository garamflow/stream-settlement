package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import org.springframework.batch.item.database.JdbcPagingItemReader;

import java.time.LocalDate;
import java.util.Map;

public interface DailyMemberViewLogCustomRepository {
    Map<String, Long> getPartitionInfo(LocalDate targetDate);

    JdbcPagingItemReader<DailyMemberViewLog> createPagingReader(
            LocalDate targetDate, Long minId, Long maxId, int pageSize);
} 