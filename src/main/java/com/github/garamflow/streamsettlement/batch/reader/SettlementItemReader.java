package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndSettlementDto;
import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class SettlementItemReader implements ItemReader<StatisticsAndSettlementDto> {

    private final ContentStatisticsRepository contentStatisticsRepository;
    private final SettlementRepository settlementRepository;
    private final Queue<StatisticsAndSettlementDto> statisticsQueue = new LinkedList<>();

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;
    @Value("#{stepExecutionContext['startStatisticsId']}")
    private Long startStatisticsId;
    @Value("#{stepExecutionContext['endStatisticsId']}")
    private Long endStatisticsId;
    @Value("${batch.chunk-size:1000}")
    private Long chunkSize;

    private Long currentStartId;

    @PostConstruct
    public void init() {
        log.info("Settlement Reader initialized - startId: {}, endId: {}", startStatisticsId, endStatisticsId);
        this.currentStartId = startStatisticsId;
    }

    @Override
    public StatisticsAndSettlementDto read() throws Exception {
        if (statisticsQueue.isEmpty() && hasNextChunk()) {
            processNextChunk();
        }
        return statisticsQueue.poll();
    }

    private boolean hasNextChunk() {
        return currentStartId <= endStatisticsId;
    }

    private void processNextChunk() {
        long fetchSize = Math.min(chunkSize, endStatisticsId - currentStartId + 1);
        fetchAndFillQueue(fetchSize);
        currentStartId += fetchSize;
    }

    private void fetchAndFillQueue(long fetchSize) {
        // 1. 통계 데이터 조회 - 이미 중복이 제거된 데이터
        List<ContentStatistics> statistics = contentStatisticsRepository
                .findByIdBetweenAndStatisticsDateOrderByContentPostId(
                        currentStartId,
                        currentStartId + fetchSize - 1,
                        LocalDate.parse(targetDate)
                );

        // 2. 이전 정산 데이터 조회
        List<Long> contentIds = statistics.stream()
                .map(stat -> stat.getContentPost().getId())
                .toList();

        Map<Long, Settlement> previousSettlements = settlementRepository
                .findByContentPostIdInAndSettlementDateBefore(contentIds, LocalDate.parse(targetDate))
                .stream()
                .collect(Collectors.groupingBy(
                        Settlement::getContentPostId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(Settlement::getSettlementDate)),
                                optional -> optional.orElse(null)
                        )
                ));

        // 3. DTO 생성 및 큐에 추가
        statistics.forEach(stat -> {
            Settlement prevSettlement = previousSettlements.get(stat.getContentPost().getId());
            statisticsQueue.offer(new StatisticsAndSettlementDto(
                    stat,
                    new PreviousSettlementDto(
                            stat.getContentPost().getId(),
                            prevSettlement != null ? prevSettlement.getTotalContentRevenue() : 0L,
                            prevSettlement != null ? prevSettlement.getTotalAdRevenue() : 0L
                    )
            ));
        });
    }
} 