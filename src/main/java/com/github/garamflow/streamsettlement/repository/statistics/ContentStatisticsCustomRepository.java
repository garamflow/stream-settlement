package com.github.garamflow.streamsettlement.repository.statistics;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.repository.common.BulkInsertable;

/**
 * 컨텐츠 통계 데이터의 벌크 삽입을 위한 커스텀 리포지토리 인터페이스입니다.
 */
public interface ContentStatisticsCustomRepository extends BulkInsertable<ContentStatistics> {
}