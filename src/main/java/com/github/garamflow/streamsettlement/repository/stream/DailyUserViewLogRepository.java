package com.github.garamflow.streamsettlement.repository.stream;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyUserViewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyUserViewLogRepository extends JpaRepository<DailyUserViewLog, Long> {

    Optional<DailyUserViewLog> findByUserIdAndContentPostId(Long userId, Long contentPostId);
}
