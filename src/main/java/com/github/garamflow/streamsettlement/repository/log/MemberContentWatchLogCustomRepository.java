package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import org.springframework.batch.item.database.JdbcPagingItemReader;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MemberContentWatchLogCustomRepository {
    Map<String, Long> getPartitionInfo(LocalDate targetDate);

    JdbcPagingItemReader<MemberContentWatchLog> createPagingReader(
            LocalDate targetDate, Long minId, Long maxId, int pageSize);

    Set<Long> findContentIdsByDate(LocalDate date);

    List<MemberContentWatchLog> findByContentPostIdAndWatchedDateWithPaging(
            Long contentPostId,
            LocalDate watchedDate,
            Long cursorId,
            Long fetchSize);

    List<DailyWatchedContent> findByLogDateAndIdGreaterThan(
            LocalDate logDate,
            Long cursorId,
            Long limit);

    List<DailyWatchedContent> findByLogDateAndContentPostIdsAndIdGreaterThan(
            LocalDate logDate,
            Set<Long> contentPostIds,
            Long cursorId,
            Long limit);
}
