package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberContentWatchLogRepository extends JpaRepository<MemberContentWatchLog, Long>, MemberContentWatchLogCustomRepository {
    Optional<MemberContentWatchLog> findByMemberIdAndContentPostId(Long memberId, Long contentPostId);
}
