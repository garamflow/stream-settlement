package com.github.garamflow.streamsettlement.repository.stream;


import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ContentPostCustomRepositoryImpl implements ContentPostCustomRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void bulkUpdateViewCounts(Map<Long, Long> viewCounts) {
        String sql = """
                UPDATE content_post
                SET total_views = COALESCE(total_views, 0) + :viewCount
                WHERE content_post_id = :contentId
                """;

        MapSqlParameterSource[] parameterSources = viewCounts.entrySet().stream()
                .map(entry -> {
                    log.debug("Updating content {} with view count {}", entry.getKey(), entry.getValue());
                    return new MapSqlParameterSource()
                            .addValue("contentId", entry.getKey())
                            .addValue("viewCount", entry.getValue());
                })
                .toArray(MapSqlParameterSource[]::new);

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, parameterSources);
        log.debug("Updated {} records", updateCounts.length);
    }

    @Override
    public List<ContentPost> findAllByIds(Set<Long> ids) {
        return entityManager.createQuery(
                        "SELECT cp FROM ContentPost cp WHERE cp.id IN :ids",
                        ContentPost.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public List<ContentPost> findAllByIdsWithCursor(Set<Long> ids, Long lastId, int limit) {
        String query = """
                    SELECT cp FROM ContentPost cp
                    WHERE cp.id IN :ids
                    AND (:lastId IS NULL OR cp.id > :lastId)
                    ORDER BY cp.id ASC
                """;

        return entityManager.createQuery(query, ContentPost.class)
                .setParameter("ids", ids)
                .setParameter("lastId", lastId)
                .setMaxResults(limit)
                .getResultList();
    }
} 