package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.batch.dto.SettlementCalculationDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndCumulativeSettlementDto;
import com.github.garamflow.streamsettlement.domain.AdRevenueRange;
import com.github.garamflow.streamsettlement.domain.ContentRevenueRange;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.exception.BatchProcessingException;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementQuerydslRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsQuerydslRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 정산 처리를 위한 데이터 읽기 구현
 * - 통계 데이터를 기반으로 정산 데이터 생성
 * - 백프레셔가 적용된 비동기 큐 사용
 * - 성능 모니터링을 위한 메트릭 수집
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementItemReader implements ItemReader<StatisticsAndCumulativeSettlementDto> {

    private final ContentStatisticsQuerydslRepository contentStatisticsQuerydslRepository;
    private final SettlementQuerydslRepository settlementQuerydslRepository;
    private final BatchProperties batchProperties;
    private final MeterRegistry meterRegistry;
    private BlockingQueue<StatisticsAndCumulativeSettlementDto> statisticsQueue;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private Long lastStatisticsId = 0L;

    /**
     * 큐 초기화
     * - 설정된 용량의 ArrayBlockingQueue 생성
     */
    @PostConstruct
    public void init() {
        this.statisticsQueue = new ArrayBlockingQueue<>(batchProperties.getReader().getQueueCapacity());
    }

    /**
     * 데이터 읽기 구현
     * - 큐가 비어있으면 다음 배치 데이터 로드
     * - 큐에서 하나의 아이템 반환
     */
    @Override
    public StatisticsAndCumulativeSettlementDto read() throws Exception {
        if (statisticsQueue.isEmpty()) {
            fetchNextBatch();
        }
        return statisticsQueue.poll();
    }

    /**
     * 다음 배치 데이터 로드
     * - 통계 데이터 조회
     * - 이전 정산 정보 조회
     * - DTO 생성 및 큐 적재
     * - 성능 측정 및 로깅
     */
    private void fetchNextBatch() {
        Timer.Sample fetchTimer = Timer.start(meterRegistry);
        try {
            List<ContentStatistics> statistics = contentStatisticsQuerydslRepository
                    .findByIdGreaterThanAndStatisticsDate(
                            lastStatisticsId,
                            targetDate,
                            batchProperties.getChunkSize()
                    );

            if (statistics.isEmpty()) {
                return;
            }

            lastStatisticsId = statistics.getLast().getId();

            List<Long> contentIds = extractContentIds(statistics);
            Map<Long, SettlementCalculationDto> prevSettlementMap = fetchPreviousSettlements(contentIds);

            List<StatisticsAndCumulativeSettlementDto> results = createStatisticsAndSettlementDtos(
                    statistics,
                    prevSettlementMap
            );

            // 백프레셔가 적용된 큐 적재
            for (StatisticsAndCumulativeSettlementDto result : results) {
                while (!statisticsQueue.offer(result, 100, TimeUnit.MILLISECONDS)) {
                    log.warn("Queue is full, waiting for space...");
                    meterRegistry.counter("batch.reader.queue.full").increment();
                }
            }

            fetchTimer.stop(meterRegistry.timer("batch.reader.fetch.time"));
            log.debug("Fetched {} settlement records, last ID: {}", results.size(), lastStatisticsId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchProcessingException("Interrupted while filling queue", e);
        }
    }

    /**
     * 통계 데이터에서 콘텐츠 ID 추출
     */
    private List<Long> extractContentIds(List<ContentStatistics> statistics) {
        return statistics.stream()
                .map(stat -> stat.getContentPost().getId())
                .toList();
    }

    /**
     * 이전 정산 정보 조회
     * - 콘텐츠 ID 목록에 대한 누적 정산 정보 조회
     */
    private Map<Long, SettlementCalculationDto> fetchPreviousSettlements(List<Long> contentIds) {
        List<SettlementCalculationDto> prevSettlements =
                settlementQuerydslRepository.findCumulativeSettlementsByContentIds(contentIds, targetDate);

        return prevSettlements.stream()
                .collect(Collectors.toMap(
                        SettlementCalculationDto::contentId,
                        dto -> dto,
                        (existing, replacement) -> existing
                ));
    }

    /**
     * 정산 DTO 생성
     * - 통계 데이터와 이전 정산 정보를 결합하여 DTO 생성
     */
    private List<StatisticsAndCumulativeSettlementDto> createStatisticsAndSettlementDtos(
            List<ContentStatistics> statistics,
            Map<Long, SettlementCalculationDto> prevSettlementMap) {

        return statistics.stream()
                .map(stat -> createStatisticsAndSettlementDto(stat, prevSettlementMap))
                .toList();
    }

    /**
     * 단일 정산 DTO 생성
     * - 현재 통계 기반 수익 계산
     * - 이전 정산 정보와 결합
     */
    private StatisticsAndCumulativeSettlementDto createStatisticsAndSettlementDto(
            ContentStatistics stat,
            Map<Long, SettlementCalculationDto> prevSettlementMap) {

        Long contentId = stat.getContentPost().getId();

        long currentContentRevenue = ContentRevenueRange.calculateTotalRevenue(stat.getAccumulatedViews());
        long currentAdRevenue = AdRevenueRange.calculateTotalRevenue(stat.getWatchTime());

        SettlementCalculationDto prevSettlement = prevSettlementMap.getOrDefault(
                contentId,
                new SettlementCalculationDto(
                        null, contentId, 0L, 0L, 0L, 0L
                )
        );

        SettlementCalculationDto calculationDto = new SettlementCalculationDto(
                null,
                contentId,
                currentContentRevenue,
                currentAdRevenue,
                prevSettlement.totalContentRevenue(),
                prevSettlement.totalAdRevenue()
        );

        return new StatisticsAndCumulativeSettlementDto(stat, calculationDto);
    }
}