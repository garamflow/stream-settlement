package com.github.garamflow.streamsettlement.controller.dto.stream.request;

import com.github.garamflow.streamsettlement.controller.dto.stream.StreamingEndType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StreamingEndRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Min(value = 1, message = "올바른 사용자 ID를 입력해주세요.")
        Long memberId,

        @NotNull(message = "컨텐츠 ID는 필수입니다.")
        @Min(value = 1, message = "올바른 컨텐츠 ID를 입력해주세요.")
        Long contentPostId,

        @NotNull(message = "최종 재생 위치는 필수입니다.")
        @Min(value = 0, message = "재생 위치는 0초 이상이어야 합니다.")
        Long finalPosition,

        @NotNull(message = "종료 유형은 필수입니다.")
        StreamingEndType endType
) {
    // 유효성 검증 추가
    public StreamingEndRequest {
        if (finalPosition != null && endType == StreamingEndType.COMPLETE && finalPosition == 0) {
            throw new IllegalArgumentException("완료 상태에서는 재생 위치가 0일 수 없습니다.");
        }
    }
}
