package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndSettlementDto;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
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
        ReflectionTestUtils.setField(processor, "targetDate", targetDate.toString());
    }

    @Test
    @DisplayName("정산 처리 - 일반적인 케이스")
    void processNormalCase() throws Exception {
        // given
        ContentPost contentPost = ContentPost.builder()
                .title("Test Content")
                .url("http://test.com/1")
                .build();
        ReflectionTestUtils.setField(contentPost, "id", 1L);

        ContentStatistics statistics = ContentStatistics.customBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(100L)
                .watchTime(1000L)
                .accumulatedViews(10000L)
                .build();

        PreviousSettlementDto previousSettlement = new PreviousSettlementDto(1L, 3000L, 2000L);
        StatisticsAndSettlementDto input = new StatisticsAndSettlementDto(statistics, previousSettlement);

        // when
        Settlement result = processor.process(input);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContentPostId()).isEqualTo(1L);
        assertThat(result.getSettlementDate()).isEqualTo(targetDate);
        assertThat(result.getTotalContentRevenue()).isGreaterThan(previousSettlement.previousTotalContentRevenue());
        assertThat(result.getTotalAdRevenue()).isGreaterThan(previousSettlement.previousTotalAdRevenue());
    }

    @Test
    @DisplayName("정산 처리 - 이전 정산이 없는 경우")
    void processWithNoPreviousSettlement() throws Exception {
        // given
        ContentPost contentPost = ContentPost.builder()
                .title("Test Content")
                .url("http://test.com/1")
                .build();
        ReflectionTestUtils.setField(contentPost, "id", 1L);

        ContentStatistics statistics = ContentStatistics.customBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(100L)
                .watchTime(1000L)
                .accumulatedViews(100L)
                .build();

        PreviousSettlementDto previousSettlement = new PreviousSettlementDto(1L, 0L, 0L);
        StatisticsAndSettlementDto input = new StatisticsAndSettlementDto(statistics, previousSettlement);

        // when
        Settlement result = processor.process(input);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContentRevenue()).isEqualTo(result.getTotalContentRevenue());
        assertThat(result.getAdRevenue()).isEqualTo(result.getTotalAdRevenue());
    }

    @Test
    @DisplayName("정산 처리 - 수익이 감소한 경우")
    void processWithDecreasedRevenue() throws Exception {
        // given
        ContentPost contentPost = ContentPost.builder()
                .title("Test Content")
                .url("http://test.com/1")
                .build();
        ReflectionTestUtils.setField(contentPost, "id", 1L);

        ContentStatistics statistics = ContentStatistics.customBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(50L)
                .watchTime(500L)
                .accumulatedViews(500L)
                .build();

        PreviousSettlementDto previousSettlement = new PreviousSettlementDto(1L, 10000L, 8000L);
        StatisticsAndSettlementDto input = new StatisticsAndSettlementDto(statistics, previousSettlement);

        // when
        Settlement result = processor.process(input);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContentRevenue()).isLessThan(0L);
        assertThat(result.getAdRevenue()).isLessThan(0L);
    }
} 