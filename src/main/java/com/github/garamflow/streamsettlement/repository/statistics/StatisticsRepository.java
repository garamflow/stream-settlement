package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StatisticsRepository extends JpaRepository<ContentStatistics, Long> {
    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByViewCountDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByWatchTimeDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );
}
