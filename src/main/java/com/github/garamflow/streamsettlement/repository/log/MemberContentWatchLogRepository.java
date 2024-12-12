package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MemberContentWatchLogRepository extends JpaRepository<MemberContentWatchLog, Long>, MemberContentWatchLogCustomRepository {
    Optional<MemberContentWatchLog> findByMemberIdAndContentPostId(Long memberId, Long contentPostId);

    List<MemberContentWatchLog> findByContentPostId(Long contentPostId);

    List<MemberContentWatchLog> findByContentPostIdAndWatchedDate(Long contentPostId, LocalDate watchedDate);
}
