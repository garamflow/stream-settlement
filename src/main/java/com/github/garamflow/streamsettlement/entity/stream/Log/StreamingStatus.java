package com.github.garamflow.streamsettlement.entity.stream.Log;

public enum StreamingStatus {
    IN_PROGRESS("시청 중"),
    PAUSED("일시 정지"),
    STOPPED("중도 종료"),
    COMPLETED("시청 완료");

    private final String description;

    StreamingStatus(String description) {
        this.description = description;
    }
}
