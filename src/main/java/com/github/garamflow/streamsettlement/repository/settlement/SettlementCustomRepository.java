package com.github.garamflow.streamsettlement.repository.settlement;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.repository.common.BulkInsertable;

/**
 * 정산 데이터의 벌크 삽입을 위한 커스텀 리포지토리 인터페이스입니다.
 */
public interface SettlementCustomRepository extends BulkInsertable<Settlement> {
}