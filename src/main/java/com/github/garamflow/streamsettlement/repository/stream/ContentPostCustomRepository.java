package com.github.garamflow.streamsettlement.repository.stream;

import java.util.Map;

public interface ContentPostCustomRepository {
    void bulkUpdateViewCounts(Map<Long, Long> viewCounts);
} 