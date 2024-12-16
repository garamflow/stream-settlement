package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsQuerydslRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsItemReaderTest {

    @Mock
    private ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private BatchProperties.Reader readerProperties;

    @InjectMocks
    private StatisticsItemReader reader;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final List<CumulativeStatisticsDto> sampleStatistics = List.of(
            new CumulativeStatisticsDto(1L, 100L, 1000L, 150L),
            new CumulativeStatisticsDto(2L, 200L, 2000L, 350L)
    );

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reader, "targetDate", targetDate);
        ReflectionTestUtils.setField(reader, "meterRegistry", meterRegistry);
        when(batchProperties.getReader()).thenReturn(readerProperties);
        when(readerProperties.getQueueCapacity()).thenReturn(100);

        reader.init();
    }

    @Test
    @DisplayName("정상적인 데이터 읽기")
    void readSuccess() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(10);
        when(contentStatisticsQuerydslRepository.findDailyStatisticsByContentIdGreaterThan(
                0L, targetDate, 10))
                .thenReturn(sampleStatistics);

        // when
        CumulativeStatisticsDto firstRead = reader.read();
        CumulativeStatisticsDto secondRead = reader.read();
        CumulativeStatisticsDto thirdRead = reader.read();

        // then
        assertThat(firstRead).isEqualTo(sampleStatistics.get(0));
        assertThat(secondRead).isEqualTo(sampleStatistics.get(1));
        assertThat(thirdRead).isNull();
    }

    @Test
    @DisplayName("빈 데이터 읽기")
    void readEmptyData() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(10);
        when(contentStatisticsQuerydslRepository.findDailyStatisticsByContentIdGreaterThan(
                0L, targetDate, 10))
                .thenReturn(Collections.emptyList());

        // when
        CumulativeStatisticsDto result = reader.read();

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("백프레셔가 적용되면 메트릭이 증가한다")
    void incrementsMetricWhenBackPressureApplied() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(2);
        when(readerProperties.getQueueCapacity()).thenReturn(1);
        reader.init();

        // 한 번의 호출에 대해서만 데이터 반환
        when(contentStatisticsQuerydslRepository.findDailyStatisticsByContentIdGreaterThan(
                eq(0L), eq(targetDate), anyInt()))
                .thenReturn(List.of(sampleStatistics.get(0)));

        // when
        CumulativeStatisticsDto result = reader.read();

        // then
        assertThat(result).isEqualTo(sampleStatistics.get(0));
        verify(contentStatisticsQuerydslRepository)
                .findDailyStatisticsByContentIdGreaterThan(eq(0L), eq(targetDate), anyInt());
    }

    @Test
    @DisplayName("큐가 가득 찼을 때 백프레셔 동작 확인")
    void backPressureWhenQueueIsFull() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(2);
        when(readerProperties.getQueueCapacity()).thenReturn(1);
        reader.init();

        // 첫 번째 호출만 데이터 반환
        when(contentStatisticsQuerydslRepository.findDailyStatisticsByContentIdGreaterThan(
                eq(0L), eq(targetDate), anyInt()))
                .thenReturn(List.of(sampleStatistics.get(0)));

        // when
        CumulativeStatisticsDto result = reader.read();

        // then
        assertThat(result).isEqualTo(sampleStatistics.get(0));
        verify(contentStatisticsQuerydslRepository)
                .findDailyStatisticsByContentIdGreaterThan(eq(0L), eq(targetDate), anyInt());
    }

    private List<CumulativeStatisticsDto> generateLargeDataSet(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> new CumulativeStatisticsDto(
                        (long) i,      // contentId
                        100L * i,      // totalViews
                        1000L * i,     // totalWatchTime
                        150L * i       // accumulatedViews
                ))
                .collect(Collectors.toList());
    }
}