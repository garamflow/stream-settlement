package com.github.garamflow.streamsettlement.service.cache;

import java.util.Map;

public interface ViewCountCacheService {
    void incrementViewCount(Long contentId);

    Map<Long, Long> fetchPreviousMinuteViewCounts(String timeWindowKey);

    void deleteProcessedKeys(String key);

    String generatePreviousMinuteViewCountKey();

    String generateViewCountKey();

    void flushAllViewCounts();
}
