package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.batch.dto.SettlementCalculationDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndCumulativeSettlementDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementQuerydslRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsQuerydslRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementItemReaderTest {

    @Mock
    private ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;

    @Mock
    private SettlementQuerydslRepository settlementQuerydslRepository;

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private BatchProperties.Reader readerProperties;

    @InjectMocks
    private SettlementItemReader reader;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reader, "targetDate", targetDate);

        // BatchProperties 설정
        when(batchProperties.getReader()).thenReturn(readerProperties);
        when(readerProperties.getQueueCapacity()).thenReturn(100);
        when(batchProperties.getChunkSize()).thenReturn(10);

        // 실제 MeterRegistry 사용
        ReflectionTestUtils.setField(reader, "meterRegistry", new SimpleMeterRegistry());

        reader.init();
    }

    @Test
    @DisplayName("정상적인 데이터 읽기")
    void readNormalData() throws Exception {
        // given
        List<ContentStatistics> statistics = createTestStatistics(1L, 5L);
        Map<Long, SettlementCalculationDto> settlements = createTestSettlements(1L, 5L);

        when(contentStatisticsQuerydslRepository.findByIdGreaterThanAndStatisticsDate(
                anyLong(), eq(targetDate), anyInt()))
                .thenReturn(statistics)
                .thenReturn(Collections.emptyList());

        when(settlementQuerydslRepository.findCumulativeSettlementsByContentIds(anyList(), eq(targetDate)))
                .thenReturn(new ArrayList<>(settlements.values()));

        // when & then
        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 5; i++) {
            StatisticsAndCumulativeSettlementDto result = reader.read();
            assertThat(result).isNotNull()
                    .satisfies(dto -> {
                        ContentPost contentPost = dto.statistics().getContentPost();
                        SettlementCalculationDto settlement = dto.cumulativeSettlementDto();

                        assertThat(contentPost.getId()).isEqualTo(counter.get() + 1);
                        assertThat(settlement.contentId()).isEqualTo(counter.get() + 1);
                        assertThat(settlement.totalContentRevenue()).isGreaterThan(0L);
                        assertThat(settlement.totalAdRevenue()).isGreaterThan(0L);
                        counter.incrementAndGet();
                    });
        }
        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("이전 정산 데이터가 없는 경우")
    void readWithNoSettlementData() throws Exception {
        // given
        List<ContentStatistics> statistics = createTestStatistics(1L, 3L);

        when(contentStatisticsQuerydslRepository.findByIdGreaterThanAndStatisticsDate(
                anyLong(), eq(targetDate), anyInt()))
                .thenReturn(statistics)
                .thenReturn(Collections.emptyList());

        when(settlementQuerydslRepository.findCumulativeSettlementsByContentIds(anyList(), eq(targetDate)))
                .thenReturn(Collections.emptyList());

        // when
        StatisticsAndCumulativeSettlementDto result = reader.read();

        // then
        assertThat(result).isNotNull()
                .satisfies(dto -> {
                    assertThat(dto.statistics().getViewCount()).isEqualTo(100L);
                    assertThat(dto.statistics().getWatchTime()).isEqualTo(1000L);
                });
    }

    @Test
    @DisplayName("빈 데이터 처리")
    void handleEmptyData() throws Exception {
        // given
        when(contentStatisticsQuerydslRepository.findByIdGreaterThanAndStatisticsDate(
                anyLong(), eq(targetDate), anyInt()))
                .thenReturn(Collections.emptyList());

        // when
        StatisticsAndCumulativeSettlementDto result = reader.read();

        // then
        assertThat(result).isNull();
    }

    private List<ContentStatistics> createTestStatistics(Long startId, Long endId) {
        List<ContentStatistics> statistics = new ArrayList<>();
        for (long i = startId; i <= endId; i++) {
            ContentPost contentPost = ContentPost.createBuilder()
                    .title("테스트 콘텐츠 " + i)
                    .url("http://test.com/" + i)
                    .build();
            ReflectionTestUtils.setField(contentPost, "id", i);
            ReflectionTestUtils.setField(contentPost, "totalViews", 1000L);

            ContentStatistics stat = ContentStatistics.existingBuilder()
                    .contentPost(contentPost)
                    .statisticsDate(targetDate)
                    .period(StatisticsPeriod.DAILY)
                    .viewCount(100L)
                    .watchTime(1000L)
                    .accumulatedViews(1000L)
                    .build();
            ReflectionTestUtils.setField(stat, "id", i);

            statistics.add(stat);
        }
        return statistics;
    }

    private Map<Long, SettlementCalculationDto> createTestSettlements(Long startId, Long endId) {
        Map<Long, SettlementCalculationDto> settlements = new HashMap<>();
        for (long i = startId; i <= endId; i++) {
            settlements.put(i, new SettlementCalculationDto(
                    i,      // id
                    i,      // contentId
                    1000L,  // totalContentRevenue
                    500L,   // totalAdRevenue
                    500L,   // previousContentRevenue
                    250L    // previousAdRevenue
            ));
        }
        return settlements;
    }
}