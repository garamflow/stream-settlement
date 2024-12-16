package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MemberContentWatchLogCustomRepositoryImpl implements MemberContentWatchLogCustomRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @Transactional
    public void bulkInsertLogs(List<MemberContentWatchLog> logs) {
        String sql = """
                INSERT INTO member_content_watch_log (member_id, content_post_id, last_playback_position, 
                                                      total_playback_time, watched_date, streaming_status)
                VALUES (:memberId, :contentPostId, :lastPlaybackPosition, :totalPlaybackTime, :watchedDate, :streamingStatus)
                """;

        MapSqlParameterSource[] parameterSources = logs.stream().map(log ->
                new MapSqlParameterSource()
                        .addValue("memberId", log.getMemberId())
                        .addValue("contentPostId", log.getContentPostId())
                        .addValue("lastPlaybackPosition", log.getLastPlaybackPosition())
                        .addValue("totalPlaybackTime", log.getTotalPlaybackTime())
                        .addValue("watchedDate", log.getWatchedDate())
                        .addValue("streamingStatus", log.getStreamingStatus().name())
        ).toArray(MapSqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(sql, parameterSources);
    }
}
