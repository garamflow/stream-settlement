package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.exception.BatchProcessingException;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQuerydslRepository;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.github.garamflow.streamsettlement.entity.stream.Log.QMemberContentWatchLog.memberContentWatchLog;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class StatisticsItemReader implements ItemReader<CumulativeStatisticsDto> {

    private final DailyWatchedContentQuerydslRepository dailyWatchedContentRepository;
    private final BatchProperties batchProperties;
    private final MeterRegistry meterRegistry;
    private BlockingQueue<CumulativeStatisticsDto> statisticsQueue;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private Long lastContentId = 0L;

    @PostConstruct
    public void init() {
        this.statisticsQueue = new ArrayBlockingQueue<>(batchProperties.getReader().getQueueCapacity());
    }

    @Override
    public CumulativeStatisticsDto read() throws Exception {
        if (statisticsQueue.isEmpty()) {
            fetchNextBatch();
        }
        return statisticsQueue.poll();
    }

    private void fetchNextBatch() {
        Timer.Sample fetchTimer = Timer.start(meterRegistry);
        try {
            List<CumulativeStatisticsDto> statistics = dailyWatchedContentRepository
                    .findDailyWatchedContentForStatistics(
                            lastContentId,
                            targetDate,
                            batchProperties.getChunkSize()
                    );

            if (statistics.isEmpty()) {
                return;
            }

            // 마지막 ID 업데이트
            lastContentId = statistics.get(statistics.size() - 1).contentId();

            // 큐에 데이터 추가 (백프레셔 적용)
            for (CumulativeStatisticsDto stat : statistics) {
                while (!statisticsQueue.offer(stat, 100, TimeUnit.MILLISECONDS)) {
                    log.warn("Queue is full, waiting for space...");
                    meterRegistry.counter("batch.reader.queue.full").increment();
                }
            }

            fetchTimer.stop(meterRegistry.timer("batch.reader.fetch.time"));
            log.debug("Fetched {} statistics records, last ID: {}", statistics.size(), lastContentId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchProcessingException("Interrupted while filling queue", e);
        }
    }
}