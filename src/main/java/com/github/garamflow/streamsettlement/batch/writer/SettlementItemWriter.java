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

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementItemWriter implements ItemWriter<Settlement> {

    private final SettlementRepository settlementRepository;

    @Override
    public void write(@NonNull Chunk<? extends Settlement> chunk) {
        List<Settlement> settlements = new ArrayList<>(chunk.getItems());

        settlementRepository.bulkInsertSettlement(settlements);
    }
}