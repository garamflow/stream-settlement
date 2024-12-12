package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberAdWatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAdWatchLogRepository extends JpaRepository<MemberAdWatchLog, Long> {
}
