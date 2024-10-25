package com.github.garamflow.streamsettlement.controller.dto.stream.response;

public record StreamingStartResponse(
        Long contentPostId,      // 컨텐츠 ID
        String title,            // 영상 제목
        String videoUrl,         // 영상 URL
        Integer lastViewedPosition, // 이어보기를 위한 마지막 시청 위치
        Integer totalDuration    // 총 재생 시간
) {
    // Builder 패턴을 위한 정적 메서드
    public static StreamingStartResponseBuilder builder() {
        return new StreamingStartResponseBuilder();
    }

    // Builder 클래스
    public static class StreamingStartResponseBuilder {
        private Long contentPostId;
        private String title;
        private String videoUrl;
        private Integer lastViewedPosition;
        private Integer totalDuration;

        public StreamingStartResponseBuilder contentPostId(Long contentPostId) {
            this.contentPostId = contentPostId;
            return this;
        }

        public StreamingStartResponseBuilder title(String title) {
            this.title = title;
            return this;
        }

        public StreamingStartResponseBuilder videoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public StreamingStartResponseBuilder lastViewedPosition(Integer lastViewedPosition) {
            this.lastViewedPosition = lastViewedPosition;
            return this;
        }

        public StreamingStartResponseBuilder totalDuration(Integer totalDuration) {
            this.totalDuration = totalDuration;
            return this;
        }

        public StreamingStartResponse build() {
            return new StreamingStartResponse(contentPostId, title, videoUrl, lastViewedPosition, totalDuration);
        }
    }
}
