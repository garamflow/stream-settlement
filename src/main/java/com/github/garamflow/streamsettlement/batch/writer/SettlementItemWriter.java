package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 정산 데이터를 DB에 저장하는 Writer 구현
 * - 청크 단위로 모아진 정산 데이터를 일괄 저장
 * - 벌크 인서트를 통한 성능 최적화
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementItemWriter implements ItemWriter<Settlement> {

    private final SettlementRepository settlementRepository;

    /**
     * 정산 데이터 저장 로직
     * - 청크 단위로 모아진 정산 데이터를 벌크 인서트
     *
     * @param chunk 저장할 정산 데이터 청크
     */
    @Override
    public void write(@NonNull Chunk<? extends Settlement> chunk) {
        List<Settlement> settlements = new ArrayList<>(chunk.getItems());
        settlementRepository.bulkInsert(settlements);
    }
}