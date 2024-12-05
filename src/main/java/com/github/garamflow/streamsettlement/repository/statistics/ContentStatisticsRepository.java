package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContentStatisticsRepository extends JpaRepository<ContentStatistics, Long> {
    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByViewCountDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    List<ContentStatistics> findTop5ByPeriodAndStatisticsDateOrderByWatchTimeDesc(
            StatisticsPeriod period, LocalDate date, Pageable pageable
    );

    List<ContentStatistics> findByPeriodAndStatisticsDate(
            StatisticsPeriod period, LocalDate date
    );
}
