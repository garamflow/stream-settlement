package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentStatisticsCustomRepository {
    void bulkInsertStatistics(List<ContentStatistics> statistics);

    List<ContentStatistics> findByDateAndPeriod(LocalDate date, StatisticsPeriod period);

    List<ContentStatistics> findTop5ByViewCount(StatisticsPeriod period, LocalDate date);

    List<ContentStatistics> findTop5ByWatchTime(StatisticsPeriod period, LocalDate date);

    long findMinIdByStatisticsDate(LocalDate date);

    long findMaxIdByStatisticsDate(LocalDate date);

    List<ContentStatistics> findByIdBetweenAndStatisticsDate(Long startId, Long endId, LocalDate date);

    List<ContentStatistics> findByContentPostIdAndStatisticsDateBetween(Long contentPostId, LocalDate startDate, LocalDate endDate);

    List<ContentStatistics> findAllByStatisticsDateAndPeriodWithFetch(LocalDate statisticsDate, StatisticsPeriod period);

    List<ContentStatistics> findByIdBetweenAndStatisticsDateOrderByContentPostId(
            Long startId, 
            Long endId, 
            LocalDate date
    );
} 