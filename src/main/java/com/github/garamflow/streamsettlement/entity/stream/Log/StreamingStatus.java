package com.github.garamflow.streamsettlement.entity.stream.Log;

import lombok.Getter;

@Getter
public enum StreamingStatus {
    IN_PROGRESS("STREAMING_IN_PROGRESS", "시청 중"),
    PAUSED("STREAMING_PAUSED", "일시 정지"),
    STOPPED("STREAMING_STOPPED", "중도 종료"),
    COMPLETED("STREAMING_COMPLETED", "시청 완료");

    private final String key;
    private final String description;

    StreamingStatus(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == STOPPED;
    }

    public boolean isActive() {
        return this == IN_PROGRESS || this == PAUSED;
    }
}
