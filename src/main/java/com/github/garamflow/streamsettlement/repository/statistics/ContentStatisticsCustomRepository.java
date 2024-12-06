package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;

import java.util.List;

public interface ContentStatisticsCustomRepository {
    void bulkInsertStatistics(List<ContentStatistics> statistics);
} 