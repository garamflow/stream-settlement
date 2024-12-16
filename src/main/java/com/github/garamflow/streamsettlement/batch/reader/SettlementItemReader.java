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

    @PostConstruct
    public void init() {
        this.statisticsQueue = new ArrayBlockingQueue<>(batchProperties.getReader().getQueueCapacity());
    }

    @Override
    public StatisticsAndCumulativeSettlementDto read() throws Exception {
        if (statisticsQueue.isEmpty()) {
            fetchNextBatch();
        }
        return statisticsQueue.poll();
    }

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

            // 마지막 ID 업데이트
            lastStatisticsId = statistics.get(statistics.size() - 1).getId();

            // 콘텐츠 ID 추출 및 이전 정산 정보 조회
            List<Long> contentIds = extractContentIds(statistics);
            Map<Long, SettlementCalculationDto> prevSettlementMap = fetchPreviousSettlements(contentIds);

            // DTO 생성
            List<StatisticsAndCumulativeSettlementDto> results = createStatisticsAndSettlementDtos(
                    statistics,
                    prevSettlementMap
            );

            // 큐에 데이터 추가 (백프레셔 적용)
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

    private List<Long> extractContentIds(List<ContentStatistics> statistics) {
        return statistics.stream()
                .map(stat -> stat.getContentPost().getId())
                .toList();
    }

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

    private List<StatisticsAndCumulativeSettlementDto> createStatisticsAndSettlementDtos(
            List<ContentStatistics> statistics,
            Map<Long, SettlementCalculationDto> prevSettlementMap) {

        return statistics.stream()
                .map(stat -> createStatisticsAndSettlementDto(stat, prevSettlementMap))
                .toList();
    }

    private StatisticsAndCumulativeSettlementDto createStatisticsAndSettlementDto(
            ContentStatistics stat,
            Map<Long, SettlementCalculationDto> prevSettlementMap) {

        Long contentId = stat.getContentPost().getId();

        // 현재 통계 기반 수익 계산
        long currentContentRevenue = ContentRevenueRange.calculateRevenueByViews(stat.getAccumulatedViews());
        long currentAdRevenue = AdRevenueRange.calculateRevenueByViews(stat.getWatchTime());

        // 이전 정산 정보 조회 (없으면 초기값)
        SettlementCalculationDto prevSettlement = prevSettlementMap.getOrDefault(
                contentId,
                new SettlementCalculationDto(
                        null,       // id
                        contentId,
                        0L,         // totalContentRevenue
                        0L,         // totalAdRevenue
                        0L,         // previousContentRevenue
                        0L          // previousAdRevenue
                )
        );

        // 정산 계산 데이터 생성
        SettlementCalculationDto calculationDto = new SettlementCalculationDto(
                null,                           // id
                contentId,
                currentContentRevenue,
                currentAdRevenue,
                prevSettlement.totalContentRevenue(),
                prevSettlement.totalAdRevenue()
        );

        return new StatisticsAndCumulativeSettlementDto(stat, calculationDto);
    }
}