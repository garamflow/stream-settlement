package com.github.garamflow.streamsettlement.entity.user;

import lombok.Getter;

@Getter
public enum Role {
    ANONYMOUS("ROLE_ANONYMOUS", "익명 사용자"),
    MEMBER("ROLE_MEMBER", "일반 회원"),
    CREATOR("ROLE_CREATOR", "컨텐츠 제작자");

    private final String key;
    private final String description;

    Role(String key, String description) {
        this.key = key;
        this.description = description;
    }
}