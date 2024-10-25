package com.github.garamflow.streamsettlement.controller.dto.stream.response;

import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;

public record StreamingPositionResponse(
        StreamingStatus status,
        boolean shouldStop,
        String message
) {
}