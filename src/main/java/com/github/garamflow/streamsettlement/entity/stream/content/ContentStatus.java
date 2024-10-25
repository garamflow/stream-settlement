package com.github.garamflow.streamsettlement.entity.stream.content;

import lombok.Getter;

@Getter
public enum ContentStatus {
    ACTIVE("CONTENT_ACTIVE", "영상 활성화"),
    INACTIVE("CONTENT_INACTIVE", "영상 비활성화"),
    DELETED("CONTENT_DELETED", "영상 삭제");

    private final String key;
    private final String description;

    ContentStatus(String key, String description) {
        this.key = key;
        this.description = description;
    }
}