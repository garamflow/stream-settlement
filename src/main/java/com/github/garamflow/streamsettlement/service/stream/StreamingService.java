package com.github.garamflow.streamsettlement.service.stream;

import com.github.garamflow.streamsettlement.controller.dto.stream.ContentPlaybackInfo;
import com.github.garamflow.streamsettlement.controller.dto.stream.StreamingEndType;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;

public interface StreamingService {
    ContentPlaybackInfo startPlayback(Long userId, Long contentId);

    StreamingStatus updatePlaybackPosition(Long userId, Long contentId, Long position);

    void endPlayback(Long userId, Long contentId, Long finalPosition, StreamingEndType endType);
}
