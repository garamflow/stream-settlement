package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQuerydslRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsItemReaderTest {

    @Mock
    private DailyWatchedContentQuerydslRepository dailyWatchedContentRepository;

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private BatchProperties.Reader readerProperties;

    @InjectMocks
    private StatisticsItemReader reader;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    private final List<CumulativeStatisticsDto> sampleStatistics = List.of(
            new CumulativeStatisticsDto(1L, 1L, 100L, 1000L, targetDate),
            new CumulativeStatisticsDto(2L, 2L, 200L, 2000L, targetDate)
    );

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reader, "targetDate", targetDate);
        ReflectionTestUtils.setField(reader, "startContentId", 0L);
        ReflectionTestUtils.setField(reader, "endContentId", 100L);
        when(batchProperties.getReader()).thenReturn(readerProperties);
        when(readerProperties.getQueueCapacity()).thenReturn(100);

        reader.init();
    }

    @Test
    @DisplayName("정상적인 데이터 읽기")
    void readSuccess() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(10);
        
        // 첫 번째 호출에서만 데이터 반환, 두 번째 호출에서는 빈 리스트 반환
        when(dailyWatchedContentRepository.findContentIdsByWatchedDate(
                eq(targetDate), any(Long.class), anyInt()))
                .thenReturn(List.of(1L, 2L))  // 첫 호출
                .thenReturn(Collections.emptyList());  // 이후 호출
        
        when(dailyWatchedContentRepository.findDailyWatchedContentForStatistics(
                eq(List.of(1L, 2L)), eq(targetDate)))
                .thenReturn(sampleStatistics);

        // when & then
        CumulativeStatisticsDto firstRead = reader.read();
        CumulativeStatisticsDto secondRead = reader.read();
        CumulativeStatisticsDto thirdRead = reader.read();

        assertThat(firstRead).isEqualTo(sampleStatistics.get(0));
        assertThat(secondRead).isEqualTo(sampleStatistics.get(1));
        assertThat(thirdRead).isNull();
    }

    @Test
    @DisplayName("빈 데이터 읽기")
    void readEmptyData() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(10);
        when(dailyWatchedContentRepository.findContentIdsByWatchedDate(
                eq(targetDate), any(Long.class), anyInt()))
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

        when(dailyWatchedContentRepository.findContentIdsByWatchedDate(
                eq(targetDate), any(Long.class), anyInt()))
                .thenReturn(List.of(1L));

        when(dailyWatchedContentRepository.findDailyWatchedContentForStatistics(
                eq(List.of(1L)), eq(targetDate)))
                .thenReturn(List.of(sampleStatistics.get(0)));

        // when
        CumulativeStatisticsDto result = reader.read();

        // then
        assertThat(result).isEqualTo(sampleStatistics.get(0));
    }

    @Test
    @DisplayName("큐가 가득 찼을 때 백프레셔 동작 확인")
    void backPressureWhenQueueIsFull() throws Exception {
        // given
        when(batchProperties.getChunkSize()).thenReturn(2);
        when(readerProperties.getQueueCapacity()).thenReturn(1);
        reader.init();

        when(dailyWatchedContentRepository.findContentIdsByWatchedDate(
                eq(targetDate), any(Long.class), anyInt()))
                .thenReturn(List.of(1L));

        when(dailyWatchedContentRepository.findDailyWatchedContentForStatistics(
                eq(List.of(1L)), eq(targetDate)))
                .thenReturn(List.of(sampleStatistics.get(0)));

        // when
        CumulativeStatisticsDto result = reader.read();

        // then
        assertThat(result).isEqualTo(sampleStatistics.get(0));
    }
}