package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContentStatisticsRepository extends JpaRepository<ContentStatistics, Long> {
    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByViewCountDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByWatchTimeDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    @Query("SELECT cs FROM ContentStatistics cs WHERE cs.period = :period AND cs.statisticsDate = :date")
    List<ContentStatistics> findByPeriodAndDate(
            @Param("period") StatisticsPeriod period,
            @Param("date") LocalDate date
    );

    List<ContentStatistics> findByPeriodAndStatisticsDate(
            StatisticsPeriod period, LocalDate date
    );
}
