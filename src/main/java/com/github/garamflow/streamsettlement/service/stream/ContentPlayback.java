package com.github.garamflow.streamsettlement.service.stream;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ContentPlayback {
    private final Long contentPostId;
    private final Long creatorId;
    private final String title;
    private final String videoUrl;
    private Long lastViewedPosition;
    private final Integer totalDuration;

    public ContentPlayback(Long contentPostId, Long creatorId, String title,
                           String videoUrl, Long lastViewedPosition, Integer totalDuration) {
        this.contentPostId = contentPostId;
        this.creatorId = creatorId;
        this.title = title;
        this.videoUrl = videoUrl;
        this.lastViewedPosition = lastViewedPosition;
        this.totalDuration = totalDuration;
    }

    public void updateLastViewedPosition(Long position) {
        this.lastViewedPosition = position;
    }
}
