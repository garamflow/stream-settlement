package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContentStatisticsRepository extends JpaRepository<ContentStatistics, Long> {
    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByViewCountDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByWatchTimeDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    @Query("SELECT s FROM ContentStatistics s JOIN FETCH s.contentPost WHERE s.statisticsDate = :date")
    List<ContentStatistics> findAllWithContentPostByDate(@Param("date") LocalDate date);

    @Query("""
                SELECT COALESCE(MAX(cs.accumulatedViews), 0)
                FROM ContentStatistics cs
                WHERE cs.contentPost.id = :contentId
                AND cs.statisticsDate < :date
                AND cs.period = :period
            """)
    Optional<Long> findLastAccumulatedViews(
            @Param("contentId") Long contentId,
            @Param("date") LocalDate date,
            @Param("period") StatisticsPeriod period
    );
}
