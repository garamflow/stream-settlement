package com.github.garamflow.streamsettlement.controller.dto.stream.request;

import java.time.LocalDate;

public record WatchRecordRequest(
        Long memberId,
        Long contentId,
        LocalDate watchedDate
) {
    public WatchRecordRequest {
        if (watchedDate == null) {
            watchedDate = LocalDate.now();
        }
    }
    
    public static WatchRecordRequest of(Long memberId, Long contentId, LocalDate watchedDate) {
        return new WatchRecordRequest(memberId, contentId, watchedDate);
    }
}
