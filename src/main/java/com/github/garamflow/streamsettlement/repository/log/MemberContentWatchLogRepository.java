package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberContentWatchLogRepository extends JpaRepository<MemberContentWatchLog, Long> {
    Optional<MemberContentWatchLog> findByMemberIdAndContentPostId(Long memberId, Long contentPostId);

    List<MemberContentWatchLog> findByContentPostId(Long contentPostId);

    List<MemberContentWatchLog> findByContentPostIdAndWatchedDate(Long contentPostId, LocalDate watchedDate);

    @Query("SELECT log FROM MemberContentWatchLog log " +
           "WHERE log.contentPostId = :contentPostId " +
           "AND log.watchedDate = :logDate " +
           "AND (:cursorId IS NULL OR log.id > :cursorId) " +
           "ORDER BY log.id ASC " +
           "LIMIT :fetchSize")
    List<MemberContentWatchLog> findByContentPostIdAndWatchedDateWithPaging(
            @Param("contentPostId") Long contentPostId,
            @Param("logDate") LocalDate logDate,
            @Param("cursorId") Long cursorId,
            @Param("fetchSize") Long fetchSize);
}
