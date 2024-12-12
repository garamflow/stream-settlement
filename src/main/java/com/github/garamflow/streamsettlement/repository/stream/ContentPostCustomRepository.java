package com.github.garamflow.streamsettlement.repository.stream;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ContentPostCustomRepository {
    void bulkUpdateViewCounts(Map<Long, Long> viewCounts);

    List<ContentPost> findAllByIds(Set<Long> ids);

    List<ContentPost> findAllByIdsWithCursor(Set<Long> ids, Long lastId, int limit);
} 