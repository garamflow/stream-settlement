package com.github.garamflow.streamsettlement.repository.stream;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ContentPostCustomRepositoryImpl implements ContentPostCustomRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void bulkUpdateViewCounts(Map<Long, Long> viewCounts) {
        String sql = """
                UPDATE content_post
                SET total_views = COALESCE(total_views, 0) + :viewCount
                WHERE content_post_id = :contentId
                """;

        MapSqlParameterSource[] parameterSources = viewCounts.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("contentId", entry.getKey())
                        .addValue("viewCount", entry.getValue()))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, parameterSources);
    }
} 