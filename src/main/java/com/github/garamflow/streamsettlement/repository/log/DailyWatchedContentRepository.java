package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyWatchedContentRepository extends JpaRepository<DailyWatchedContent, Long> {
    boolean existsByContentPostIdAndWatchedDate(Long contentPostId, LocalDate watchedDate);

    Optional<DailyWatchedContent> findFirstByContentPostIdAndWatchedDateOrderByIdDesc(
            Long contentPostId, LocalDate watchedDate);
}
