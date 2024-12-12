package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Primary
public interface ContentStatisticsRepository extends JpaRepository<ContentStatistics, Long>, ContentStatisticsCustomRepository {
    Optional<ContentStatistics> findByContentPost_IdAndPeriodAndStatisticsDate(
            Long contentPostId,
            StatisticsPeriod period,
            LocalDate statisticsDate
    );

    List<ContentStatistics> findByStatisticsDateAndPeriod(LocalDate statisticsDate, StatisticsPeriod period);

    List<ContentStatistics> findByContentPostIdAndStatisticsDateBetweenAndPeriod(
            Long contentPostId,
            LocalDate startDate,
            LocalDate endDate,
            StatisticsPeriod period
    );
}
