package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.exception.BatchProcessingException;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentQuerydslRepository;
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

/**
 * 통계 처리를 위한 데이터 읽기 구현
 * - 파티션별로 할당된 컨텐츠 ID 범위의 데이터를 읽음
 * - 블로킹 큐를 사용하여 메모리 사용량 제어
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class StatisticsItemReader implements ItemReader<CumulativeStatisticsDto> {

    private final DailyWatchedContentQuerydslRepository dailyWatchedContentRepository;
    private final BatchProperties batchProperties;
    private BlockingQueue<CumulativeStatisticsDto> statisticsQueue;

    // 처리 대상 날짜 (Job Parameter)
    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    // 파티션에 할당된 컨텐츠 ID 범위 (Partition Context)
    @Value("#{stepExecutionContext['startContentId']}")
    private Long startContentId;

    @Value("#{stepExecutionContext['endContentId']}")
    private Long endContentId;

    // 마지막으로 처리한 컨텐츠 ID
    private Long lastContentId;

    /**
     * 초기화
     * - 블로킹 큐 생성
     * - 시작 컨텐츠 ID 설정
     */
    @PostConstruct
    public void init() {
        this.statisticsQueue = new ArrayBlockingQueue<>(batchProperties.getReader().getQueueCapacity());
        this.lastContentId = startContentId - 1;
    }

    /**
     * 데이터 읽기 로직
     * - 할당된 ID 범위 내의 컨텐츠 데이터를 청크 단위로 조회
     * - 블로킹 큐를 통한 백프레셔 구현
     * - 메모리 부하 방지를 위한 페이징 처리
     * 
     * @return 다음 처리할 통계 데이터, 더 이상 처리할 데이터가 없으면 null
     */
    @Override
    public CumulativeStatisticsDto read() throws Exception {
        // 큐가 비어있으면 다음 청크 데이터 로드
        if (statisticsQueue.isEmpty()) {
            // 파티션의 끝에 도달했는지 확인
            if (lastContentId >= endContentId) {
                return null;
            }

            // 다음 청크 크기만큼의 컨텐츠 ID 조회
            List<Long> contentIds = dailyWatchedContentRepository
                    .findContentIdsByWatchedDate(
                            targetDate,
                            lastContentId,
                            (int) Math.min(Integer.MAX_VALUE, 
                                Math.min(endContentId - lastContentId, 
                                    batchProperties.getChunkSize()))
                    );

            if (contentIds.isEmpty()) {
                return null;
            }

            log.debug("Reading contents from ID {} to {} for date {}",
                    lastContentId, endContentId, targetDate);

            // 조회된 ID들의 통계 데이터 로드
            List<CumulativeStatisticsDto> statistics = dailyWatchedContentRepository
                    .findDailyWatchedContentForStatistics(contentIds, targetDate);

            // 통계 데이터를 큐에 적재
            statistics.forEach(stat -> {
                try {
                    // 파티션 범위 체크
                    if (stat.contentId() > endContentId) {
                        log.warn("Content ID {} is beyond partition end ID {}",
                                stat.contentId(), endContentId);
                        return;
                    }

                    // 백프레셔 구현: 타임아웃을 통한 큐 적재 제어
                    if (!statisticsQueue.offer(stat, 100, TimeUnit.MILLISECONDS)) {
                        throw new BatchProcessingException("Failed to add item to queue: timeout occurred");
                    }
                    lastContentId = stat.contentId();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BatchProcessingException("Queue operation interrupted", e);
                }
            });
        }
        return statisticsQueue.poll();
    }
}