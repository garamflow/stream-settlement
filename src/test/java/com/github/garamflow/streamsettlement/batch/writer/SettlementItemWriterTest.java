package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementItemWriterTest {

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementItemWriter writer;

    @Test
    @DisplayName("정산 데이터 일괄 저장")
    void writeBulkSettlements() throws Exception {
        // given
        List<Settlement> settlements = createTestSettlements(5);
        Chunk<Settlement> chunk = new Chunk<>(settlements);

        // when
        writer.write(chunk);

        // then
        verify(settlementRepository, times(1)).bulkInsertSettlement(settlements);
    }

    @Test
    @DisplayName("빈 청크 처리")
    void writeEmptyChunk() throws Exception {
        // given
        Chunk<Settlement> chunk = new Chunk<>(List.of());

        // when
        writer.write(chunk);

        // then
        verify(settlementRepository, times(1)).bulkInsertSettlement(List.of());
    }

    private List<Settlement> createTestSettlements(int count) {
        List<Settlement> settlements = new ArrayList<>();
        LocalDate settlementDate = LocalDate.of(2024, 1, 1);

        for (long i = 1; i <= count; i++) {
            Settlement settlement = Settlement.customBuilder()
                    .contentPostId(i)
                    .contentRevenue(1000L)
                    .adRevenue(500L)
                    .totalContentRevenue(5000L)
                    .totalAdRevenue(2500L)
                    .settlementDate(settlementDate)
                    .build();
            settlements.add(settlement);
        }
        return settlements;
    }
} 