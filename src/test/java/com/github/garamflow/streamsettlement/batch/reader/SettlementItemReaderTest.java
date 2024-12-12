package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndSettlementDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementItemReaderTest {

    @Mock
    private ContentStatisticsRepository contentStatisticsRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private SettlementItemReader reader;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);
    private final Long startId = 1L;
    private final Long endId = 100L;
    private final Long chunkSize = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reader, "targetDate", targetDate.toString());
        ReflectionTestUtils.setField(reader, "startStatisticsId", startId);
        ReflectionTestUtils.setField(reader, "endStatisticsId", endId);
        ReflectionTestUtils.setField(reader, "chunkSize", chunkSize);
        reader.init();
    }

    @Test
    @DisplayName("정상적인 데이터 읽기")
    void readNormalData() throws Exception {
        // given
        List<ContentStatistics> statistics = createTestStatistics(1L, 5L);
        List<PreviousSettlementDto> previousSettlements = createTestPreviousSettlements(1L, 5L);

        when(contentStatisticsRepository.findByIdBetweenAndStatisticsDate(
                anyLong(), anyLong(), eq(targetDate)))
                .thenReturn(statistics);
        when(settlementRepository.findPreviousSettlementsByContentIds(anyList(), eq(targetDate)))
                .thenReturn(previousSettlements);

        // when & then
        for (int i = 0; i < 5; i++) {
            StatisticsAndSettlementDto result = reader.read();
            assertThat(result).isNotNull();
            assertThat(result.statistics().getContentPost().getId()).isEqualTo(i + 1);
            assertThat(result.previousSettlement().contentPostId()).isEqualTo(i + 1);
        }
        assertThat(reader.read()).isNull(); // 더 이상 읽을 데이터가 없음
    }

    @Test
    @DisplayName("이전 정산 데이터가 없는 경우")
    void readWithNoPreviousSettlement() throws Exception {
        // given
        List<ContentStatistics> statistics = createTestStatistics(1L, 3L);
        when(contentStatisticsRepository.findByIdBetweenAndStatisticsDate(
                anyLong(), anyLong(), eq(targetDate)))
                .thenReturn(statistics);
        when(settlementRepository.findPreviousSettlementsByContentIds(anyList(), eq(targetDate)))
                .thenReturn(List.of());

        // when
        StatisticsAndSettlementDto result = reader.read();

        // then
        assertThat(result).isNotNull();
        assertThat(result.previousSettlement().previousTotalContentRevenue()).isZero();
        assertThat(result.previousSettlement().previousTotalAdRevenue()).isZero();
    }

    @Test
    @DisplayName("청크 단위로 데이터 읽기")
    void readByChunks() throws Exception {
        // given
        List<ContentStatistics> firstChunk = createTestStatistics(1L, 10L);
        List<ContentStatistics> secondChunk = createTestStatistics(11L, 20L);

        when(contentStatisticsRepository.findByIdBetweenAndStatisticsDate(
                eq(1L), eq(10L), eq(targetDate)))
                .thenReturn(firstChunk);
        when(contentStatisticsRepository.findByIdBetweenAndStatisticsDate(
                eq(11L), eq(20L), eq(targetDate)))
                .thenReturn(secondChunk);

        when(settlementRepository.findPreviousSettlementsByContentIds(anyList(), eq(targetDate)))
                .thenReturn(List.of());

        // when & then
        for (int i = 0; i < 20; i++) {
            StatisticsAndSettlementDto result = reader.read();
            assertThat(result).isNotNull();
            assertThat(result.statistics().getContentPost().getId()).isEqualTo(i + 1);
        }
    }

    @Test
    @DisplayName("빈 데이터 처리")
    void handleEmptyData() throws Exception {
        // given
        when(contentStatisticsRepository.findByIdBetweenAndStatisticsDate(
                anyLong(), anyLong(), eq(targetDate)))
                .thenReturn(List.of());

        // when
        StatisticsAndSettlementDto result = reader.read();

        // then
        assertThat(result).isNull();
    }

    private List<ContentStatistics> createTestStatistics(Long startId, Long endId) {
        List<ContentStatistics> statistics = new ArrayList<>();
        for (long i = startId; i <= endId; i++) {
            ContentPost contentPost = ContentPost.builder()
                    .title("Test Content " + i)
                    .url("http://test.com/" + i)
                    .build();
            ReflectionTestUtils.setField(contentPost, "id", i);

            ContentStatistics stat = ContentStatistics.customBuilder()
                    .contentPost(contentPost)
                    .statisticsDate(targetDate)
                    .period(StatisticsPeriod.DAILY)
                    .viewCount(100L)
                    .watchTime(1000L)
                    .build();
            ReflectionTestUtils.setField(stat, "id", i);

            statistics.add(stat);
        }
        return statistics;
    }

    private List<PreviousSettlementDto> createTestPreviousSettlements(Long startId, Long endId) {
        List<PreviousSettlementDto> settlements = new ArrayList<>();
        for (long i = startId; i <= endId; i++) {
            settlements.add(new PreviousSettlementDto(i, 50L, 500L));
        }
        return settlements;
    }
} 