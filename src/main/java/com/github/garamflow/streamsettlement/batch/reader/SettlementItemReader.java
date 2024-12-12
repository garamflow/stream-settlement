package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.dto.PreviousSettlementDto;
import com.github.garamflow.streamsettlement.batch.dto.StatisticsAndSettlementDto;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
        // 1. 통계 데이터 조회
        List<ContentStatistics> statistics = contentStatisticsRepository
                .findByIdBetweenAndStatisticsDate(
                        currentStartId,
                        currentStartId + fetchSize - 1,
                        LocalDate.parse(targetDate)
                );

        // 2. 콘텐츠 ID 목록 추출
        List<Long> contentIds = statistics.stream()
                .map(stat -> stat.getContentPost().getId())
                .distinct()
                .toList();

        // 3. 이전 정산 데이터 조회
        List<PreviousSettlementDto> previousSettlements = settlementRepository
                .findPreviousSettlementsByContentIds(contentIds, LocalDate.parse(targetDate));

        Map<Long, PreviousSettlementDto> settlementMap = previousSettlements.stream()
                .collect(Collectors.toMap(
                        PreviousSettlementDto::getContentPostId,
                        dto -> dto,
                        (existing, replacement) -> existing // 중복 시 기존 값 유지
                ));

        // 4. 데이터 매핑 및 큐에 추가
        statistics.stream()
                .collect(Collectors.groupingBy(stat -> stat.getContentPost().getId()))
                .forEach((contentId, stats) -> {
                    ContentStatistics latestStat = stats.get(0); // 각 콘텐츠의 첫 번째 통계만 사용
                    PreviousSettlementDto prevSettlement = settlementMap.getOrDefault(
                            contentId,
                            new PreviousSettlementDto(contentId, 0L, 0L)
                    );
                    statisticsQueue.offer(new StatisticsAndSettlementDto(latestStat, prevSettlement));
                });
    }
} 