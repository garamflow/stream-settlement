package com.github.garamflow.streamsettlement.batch.partition;

import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsQuerydslRepository;
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
    private ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;

    @InjectMocks
    private SettlementPartitioner partitioner;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(partitioner, "targetDate", targetDate);
    }

    @Test
    @DisplayName("데이터가 없을 때 빈 파티션 생성")
    void createEmptyPartitionWhenNoData() {
        // given
        when(contentStatisticsQuerydslRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(0L);
        when(contentStatisticsQuerydslRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(0L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result)
                .hasSize(1)
                .containsKey("settlement-partition0")
                .satisfies(map -> {
                    ExecutionContext context = map.get("settlement-partition0");
                    assertThat(context.getLong("startStatisticsId")).isZero();
                    assertThat(context.getLong("endStatisticsId")).isZero();
                    assertThat(context.getString("targetDate")).isEqualTo(targetDate.toString());
                });
    }

    @Test
    @DisplayName("소량 데이터일 때 파티션 생성")
    void createPartitionsForSmallData() {
        // given
        when(contentStatisticsQuerydslRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(1L);
        when(contentStatisticsQuerydslRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(5L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result)
                .hasSize(3)
                .containsKeys(
                        "settlement-partition1",
                        "settlement-partition2",
                        "settlement-partition3"
                )
                .satisfies(map -> {
                    // 첫 번째 파티션 검증
                    ExecutionContext firstPartition = map.get("settlement-partition1");
                    assertThat(firstPartition.getLong("startStatisticsId")).isEqualTo(1L);
                    assertThat(firstPartition.getLong("endStatisticsId")).isEqualTo(2L);

                    // 두 번째 파티션 검증
                    ExecutionContext secondPartition = map.get("settlement-partition2");
                    assertThat(secondPartition.getLong("startStatisticsId")).isEqualTo(3L);
                    assertThat(secondPartition.getLong("endStatisticsId")).isEqualTo(4L);

                    // 세 번째 파티션 검증
                    ExecutionContext thirdPartition = map.get("settlement-partition3");
                    assertThat(thirdPartition.getLong("startStatisticsId")).isEqualTo(5L);
                    assertThat(thirdPartition.getLong("endStatisticsId")).isEqualTo(5L);

                    // 모든 파티션의 targetDate 검증
                    assertThat(map.values())
                            .allSatisfy(context ->
                                    assertThat(context.getString("targetDate")).isEqualTo(targetDate.toString())
                            );
                });
    }

    @Test
    @DisplayName("대량 데이터일 때 다중 파티션 생성")
    void createMultiplePartitionsForLargeData() {
        // given
        when(contentStatisticsQuerydslRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(1L);
        when(contentStatisticsQuerydslRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(100L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result)
                .hasSize(4)
                .containsKeys(
                        "settlement-partition1",
                        "settlement-partition2",
                        "settlement-partition3",
                        "settlement-partition4"
                )
                .allSatisfy((key, context) -> {
                    assertThat(context.getLong("startStatisticsId")).isGreaterThanOrEqualTo(1L);
                    assertThat(context.getLong("endStatisticsId")).isLessThanOrEqualTo(100L);
                    assertThat(context.getString("targetDate")).isEqualTo(targetDate.toString());
                });

        // 파티션 범위 검증
        ExecutionContext firstPartition = result.get("settlement-partition1");
        ExecutionContext lastPartition = result.get("settlement-partition4");

        assertThat(firstPartition.getLong("startStatisticsId")).isEqualTo(1L);
        assertThat(firstPartition.getLong("endStatisticsId")).isEqualTo(25L);
        assertThat(lastPartition.getLong("startStatisticsId")).isEqualTo(76L);
        assertThat(lastPartition.getLong("endStatisticsId")).isEqualTo(100L);
    }

    @Test
    @DisplayName("파티션 크기가 1 이상인지 확인")
    void ensureMinimumPartitionSize() {
        // given
        when(contentStatisticsQuerydslRepository.findMinIdByStatisticsDate(targetDate)).thenReturn(1L);
        when(contentStatisticsQuerydslRepository.findMaxIdByStatisticsDate(targetDate)).thenReturn(2L);

        // when
        Map<String, ExecutionContext> result = partitioner.partition(4);

        // then
        assertThat(result.values())
                .allSatisfy(context -> {
                    long start = context.getLong("startStatisticsId");
                    long end = context.getLong("endStatisticsId");
                    assertThat(end - start + 1).isGreaterThanOrEqualTo(1);
                });
    }
} 