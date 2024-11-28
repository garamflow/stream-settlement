package com.github.garamflow.streamsettlement.batch.reader.mapper;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
@RequiredArgsConstructor
public class DailyMemberViewLogRowMapper implements RowMapper<DailyMemberViewLog> {
    
    private final MemberRepository memberRepository;
    private final ContentPostRepository contentPostRepository;

    @Override
    public DailyMemberViewLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        // 기존 엔티티를 조회
        Member member = memberRepository.findById(rs.getLong("member_id"))
                .orElseThrow(() -> new IllegalStateException("Member not found"));
        
        ContentPost contentPost = contentPostRepository.findById(rs.getLong("content_post_id"))
                .orElseThrow(() -> new IllegalStateException("ContentPost not found"));

        return DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)
                .lastViewedPosition(rs.getInt("last_viewed_position"))
                .lastAdViewCount(rs.getInt("last_ad_view_count"))
                .logDate(rs.getDate("log_date").toLocalDate())
                .status(StreamingStatus.valueOf(rs.getString("streaming_status")))
                .build();
    }
}