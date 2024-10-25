package com.github.garamflow.streamsettlement.controller.dto.stream.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStreamingPositionRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId,

        @NotNull(message = "컨텐츠 게시글 ID는 필수입니다.")
        Long contentPostId,

        @NotNull(message = "재생 위치는 필수입니다.")
        @Min(value = 0, message = "재생 위치는 0초 이상이어야 합니다.")
        Integer positionInSeconds
) {
}