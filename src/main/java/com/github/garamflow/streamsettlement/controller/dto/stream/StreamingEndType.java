package com.github.garamflow.streamsettlement.controller.dto.stream;

public enum StreamingEndType {
    COMPLETE("영상 시청 완료"),
    PAUSE("일시 정지"),
    STOP("중도 종료");

    private final String description;

    StreamingEndType(String description) {
        this.description = description;
    }
}
