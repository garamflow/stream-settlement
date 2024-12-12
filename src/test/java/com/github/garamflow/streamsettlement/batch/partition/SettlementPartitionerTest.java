package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
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
import org.springframework.batch.item.ExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementPartitionerTest {
    @Mock
    private ContentStatisticsRepository contentStatisticsRepository;

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private BatchProperties.Partition partition;

    @InjectMocks
    private SettlementPartitioner partitioner;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(partitioner, "targetDate", targetDate.toString());
    }

    @Test
    @DisplayName("정산 통계 데이터가 있을 때 파티션 생성")
    void createPartitionsWithData() {
        // given
        setupBatchProperties(100L);  // 중간 크기 데이터 설정
        when(contentStatisticsRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(1L);
        when(contentStatisticsRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(100L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result).hasSize(8)
                .containsKeys(
                        "settlement-partition1",
                        "settlement-partition2",
                        "settlement-partition3",
                        "settlement-partition4",
                        "settlement-partition5",
                        "settlement-partition6",
                        "settlement-partition7",
                        "settlement-partition8"
                )
                .allSatisfy((key, context) -> {
                    assertThat(context.getLong("startStatisticsId")).isGreaterThanOrEqualTo(1L);
                    assertThat(context.getLong("endStatisticsId")).isLessThanOrEqualTo(100L);
                    assertThat(context.getString("targetDate")).isEqualTo(targetDate.toString());
                });
    }

    @Test
    @DisplayName("파티션 범위가 올바르게 계산되는지 확인")
    void verifyPartitionRanges() {
        // given
        setupBatchProperties(100L);  // 중간 크기 데이터 설정
        when(contentStatisticsRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(1L);
        when(contentStatisticsRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(100L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        ExecutionContext partition1 = result.get("settlement-partition1");
        ExecutionContext partition8 = result.get("settlement-partition8");

        assertThat(partition1.getLong("startStatisticsId")).isEqualTo(1L);
        assertThat(partition1.getLong("endStatisticsId")).isEqualTo(13L);
        assertThat(partition8.getLong("startStatisticsId")).isEqualTo(92L);
        assertThat(partition8.getLong("endStatisticsId")).isEqualTo(100L);
    }

    @Test
    @DisplayName("단일 파티션 생성 (데이터가 적을 때)")
    void createSinglePartition() {
        // given
        setupBatchProperties(2L);  // 작은 크기 데이터 설정
        when(contentStatisticsRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(1L);
        when(contentStatisticsRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(2L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result).hasSize(1)
                .containsKey("settlement-partition1")
                .satisfies(map -> {
                    ExecutionContext context = map.get("settlement-partition1");
                    assertThat(context.getLong("startStatisticsId")).isEqualTo(1L);
                    assertThat(context.getLong("endStatisticsId")).isEqualTo(2L);
                    assertThat(context.getString("targetDate")).isEqualTo(targetDate.toString());
                });
    }

    @Test
    @DisplayName("데이터가 없을 때 빈 파티션 생성")
    void createEmptyPartitionWhenNoData() {
        // given
        when(contentStatisticsRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(0L);
        when(contentStatisticsRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(0L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result).hasSize(1)
                .containsKey("settlement-partition0")
                .satisfies(map -> {
                    ExecutionContext context = map.get("settlement-partition0");
                    assertThat(context.getLong("startStatisticsId")).isEqualTo(0L);
                    assertThat(context.getLong("endStatisticsId")).isEqualTo(0L);
                    assertThat(context.getString("targetDate")).isEqualTo(targetDate.toString());
                });
    }

    private void setupBatchProperties(long dataSize) {
        when(batchProperties.getPartition()).thenReturn(partition);
        when(partition.getSmallDataSize()).thenReturn(10L);
        when(partition.getMediumDataSize()).thenReturn(100L);
        when(partition.getLargeDataSize()).thenReturn(1000L);
        when(partition.getSmallGridSize()).thenReturn(1);
        when(partition.getMediumGridSize()).thenReturn(4);
        when(partition.getLargeGridSize()).thenReturn(8);
        when(partition.getExtraLargeGridSize()).thenReturn(16);
    }
} 