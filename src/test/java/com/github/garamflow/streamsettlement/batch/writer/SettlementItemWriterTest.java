package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import lombok.Builder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementItemWriterTest {

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementItemWriter writer;

    private final LocalDate settlementDate = LocalDate.of(2024, 1, 1);

    @Test
    @DisplayName("정산 데이터 일괄 저장")
    void writeBulkSettlements() throws Exception {
        // given
        List<Settlement> settlements = createTestSettlements(
                TestSettlementBuilder.builder()
                        .count(5)
                        .contentRevenue(1000L)
                        .adRevenue(500L)
                        .totalContentRevenue(5000L)
                        .totalAdRevenue(2500L)
                        .build()
        );
        Chunk<Settlement> chunk = new Chunk<>(settlements);

        // when
        writer.write(chunk);

        // then
        verify(settlementRepository).bulkInsertSettlement(argThat(savedSettlements -> {
            assertThat(savedSettlements).hasSize(5)
                    .allSatisfy(settlement -> {
                        assertThat(settlement.getContentRevenue()).isEqualTo(1000L);
                        assertThat(settlement.getAdRevenue()).isEqualTo(500L);
                        assertThat(settlement.getTotalContentRevenue()).isEqualTo(5000L);
                        assertThat(settlement.getTotalAdRevenue()).isEqualTo(2500L);
                        assertThat(settlement.getSettlementDate()).isEqualTo(settlementDate);
                    });
            return true;
        }));
    }

    @Test
    @DisplayName("빈 청크 처리")
    void writeEmptyChunk() throws Exception {
        // given
        Chunk<Settlement> chunk = new Chunk<>(List.of());

        // when
        writer.write(chunk);

        // then
        verify(settlementRepository).bulkInsertSettlement(argThat(List::isEmpty));
    }

    private List<Settlement> createTestSettlements(TestSettlementBuilder builder) {
        List<Settlement> settlements = new ArrayList<>();

        for (long i = 1; i <= builder.count; i++) {
            Settlement settlement = Settlement.existingBuilder()
                    .contentPostId(i)
                    .contentRevenue(builder.contentRevenue)
                    .adRevenue(builder.adRevenue)
                    .totalContentRevenue(builder.totalContentRevenue)
                    .totalAdRevenue(builder.totalAdRevenue)
                    .settlementDate(settlementDate)
                    .build();
            settlements.add(settlement);
        }
        return settlements;
    }

    @Builder
    private static class TestSettlementBuilder {
        private int count;
        private Long contentRevenue;
        private Long adRevenue;
        private Long totalContentRevenue;
        private Long totalAdRevenue;
    }
}