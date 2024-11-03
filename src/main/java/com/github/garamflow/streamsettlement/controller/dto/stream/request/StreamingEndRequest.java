package com.github.garamflow.streamsettlement.controller.dto.stream.request;

import com.github.garamflow.streamsettlement.controller.dto.stream.StreamingEndType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StreamingEndRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long memberId,

        @NotNull(message = "컨텐츠 ID는 필수입니다.")
        Long contentPostId,

        @NotNull(message = "최종 재생 위치는 필수입니다.")
        @Min(value = 0, message = "재생 위치는 0초 이상이어야 합니다.")
        Integer finalPosition,

        @NotNull(message = "종료 유형은 필수입니다.")
        StreamingEndType endType
) {
}
