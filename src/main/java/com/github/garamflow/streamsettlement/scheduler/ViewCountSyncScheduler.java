package com.github.garamflow.streamsettlement.scheduler;

import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.service.cache.ViewCountCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountSyncScheduler {

    private final ContentPostRepository contentPostRepository;
    private final ViewCountCacheService viewCountCacheService;

    /**
     * 매분 5초에 실행되어 이전 1분간의 영상 조회수를 DB에 반영합니다.
     * 예시: 16:01:05에 실행되면 16:00:00~16:00:59 동안의 조회수 집계
     */
    @Scheduled(cron = "5 * * * * *")
    @Transactional
    public void syncContentViewCountsToDatabase() {
        String timeWindowKey = viewCountCacheService.generatePreviousMinuteViewCountKey();

        Map<Long, Long> viewCounts = viewCountCacheService.fetchPreviousMinuteViewCounts(timeWindowKey);

        if (!viewCounts.isEmpty()) {
            try {
                contentPostRepository.bulkUpdateViewCounts(viewCounts);
                log.info("Updated view counts in database");
                viewCountCacheService.deleteProcessedKeys(timeWindowKey);
                log.info("Deleted processed Redis keys");
            } catch (Exception e) {
                log.error("Failed to sync view counts to database", e);
                throw new RuntimeException("Error syncing view counts", e);
            }
        } else {
            log.info("No view counts to sync");
        }
    }
}