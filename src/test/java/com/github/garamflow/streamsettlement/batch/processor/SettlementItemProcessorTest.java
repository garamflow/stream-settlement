package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.SettlementCalculationDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndCumulativeSettlementDto;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import lombok.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SettlementItemProcessorTest {

    @InjectMocks
    private SettlementItemProcessor processor;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "targetDate", targetDate);
    }

    @Test
    @DisplayName("정산 처리 - 일반적인 케이스")
    void processNormalCase() throws Exception {
        // given
        StatisticsAndCumulativeSettlementDto input = createTestData(
                TestDataBuilder.builder()
                        .contentId(1L)
                        .viewCount(100L)
                        .watchTime(1000L)
                        .accumulatedViews(10000L)
                        .totalContentRevenue(5000L)
                        .totalAdRevenue(3000L)
                        .previousContentRevenue(3000L)
                        .previousAdRevenue(2000L)
                        .build()
        );

        // when
        Settlement result = processor.process(input);

        // then
        assertThat(result).isNotNull()
                .satisfies(settlement -> {
                    assertThat(settlement.getContentPostId()).isEqualTo(1L);
                    assertThat(settlement.getSettlementDate()).isEqualTo(targetDate);
                    assertThat(settlement.getContentRevenue()).isEqualTo(2000L);
                    assertThat(settlement.getAdRevenue()).isEqualTo(1000L);
                });
    }

    @Test
    @DisplayName("정산 처리 - 이전 정산이 없는 경우")
    void processWithNoPreviousSettlement() throws Exception {
        // given
        StatisticsAndCumulativeSettlementDto input = createTestData(
                TestDataBuilder.builder()
                        .contentId(1L)
                        .viewCount(100L)
                        .watchTime(1000L)
                        .accumulatedViews(100L)
                        .totalContentRevenue(1000L)
                        .totalAdRevenue(500L)
                        .previousContentRevenue(0L)
                        .previousAdRevenue(0L)
                        .build()
        );

        // when
        Settlement result = processor.process(input);

        // then
        assertThat(result).isNotNull()
                .satisfies(settlement -> {
                    assertThat(settlement.getContentRevenue()).isEqualTo(1000L);
                    assertThat(settlement.getAdRevenue()).isEqualTo(500L);
                });
    }

    @Test
    @DisplayName("정산 처리 - 수익이 감소한 경우")
    void processWithDecreasedRevenue() throws Exception {
        // given
        StatisticsAndCumulativeSettlementDto input = createTestData(
                TestDataBuilder.builder()
                        .contentId(1L)
                        .viewCount(50L)
                        .watchTime(500L)
                        .accumulatedViews(500L)
                        .totalContentRevenue(8000L)
                        .totalAdRevenue(6000L)
                        .previousContentRevenue(10000L)
                        .previousAdRevenue(8000L)
                        .build()
        );

        // when
        Settlement result = processor.process(input);

        // then
        assertThat(result).isNotNull()
                .satisfies(settlement -> {
                    assertThat(settlement.getContentRevenue()).isZero();
                    assertThat(settlement.getAdRevenue()).isZero();
                });
    }

    private StatisticsAndCumulativeSettlementDto createTestData(TestDataBuilder builder) {
        ContentPost contentPost = ContentPost.createBuilder()
                .title("Test Content")
                .url("http://test.com/" + builder.contentId)
                .build();
        ReflectionTestUtils.setField(contentPost, "id", builder.contentId);

        ContentStatistics statistics = ContentStatistics.existingBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(builder.viewCount)
                .watchTime(builder.watchTime)
                .accumulatedViews(builder.accumulatedViews)
                .build();

        SettlementCalculationDto calculationDto = new SettlementCalculationDto(
                builder.contentId,
                builder.contentId,
                builder.totalContentRevenue,
                builder.totalAdRevenue,
                builder.previousContentRevenue,
                builder.previousAdRevenue
        );

        return new StatisticsAndCumulativeSettlementDto(statistics, calculationDto);
    }

    @Builder
    private static class TestDataBuilder {
        private Long contentId;
        private Long viewCount;
        private Long watchTime;
        private Long accumulatedViews;
        private Long totalContentRevenue;
        private Long totalAdRevenue;
        private Long previousContentRevenue;
        private Long previousAdRevenue;
    }
}