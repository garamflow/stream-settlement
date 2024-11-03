package com.github.garamflow.streamsettlement.repository.stream;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyMemberViewLogRepository extends JpaRepository<DailyMemberViewLog, Long> {

    Optional<DailyMemberViewLog> findByMemberIdAndContentPostId(Long memberId, Long contentPostId);
}
