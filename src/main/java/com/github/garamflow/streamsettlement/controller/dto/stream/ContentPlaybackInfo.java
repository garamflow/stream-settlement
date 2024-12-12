package com.github.garamflow.streamsettlement.controller.dto.stream;

import com.github.garamflow.streamsettlement.service.stream.ContentPlayback;

// 서비스 계층의 도메인 객체
public record ContentPlaybackInfo(
        Long contentPostId,
        Long creatorId,
        String title,
        String videoUrl,
        Long lastViewedPosition,
        Integer totalDuration
) {
    // DTO를 도메인 객체로 변환하는 메서드
    public ContentPlayback toDomain() {
        return new ContentPlayback(
                contentPostId,
                creatorId,
                title,
                videoUrl,
                lastViewedPosition,
                totalDuration
        );
    }

    // 도메인 객체를 DTO로 변환하는 정적 메서드
    public static ContentPlaybackInfo fromDomain(ContentPlayback domain) {
        return new ContentPlaybackInfo(
                domain.getContentPostId(),
                domain.getCreatorId(),
                domain.getTitle(),
                domain.getVideoUrl(),
                domain.getLastViewedPosition(),
                domain.getTotalDuration()
        );
    }
}

