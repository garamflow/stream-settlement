package com.github.garamflow.streamsettlement.batch.dto;

import java.time.LocalDate;

/**
 * 일일 콘텐츠 시청 통계 집계 데이터 전달 객체
 * - 특정 날짜의 콘텐츠별 조회수와 총 시청시간을 집계하여 통계 처리에 사용
 */
public record CumulativeStatisticsDto(
        Long id,              // 로그 ID
        Long contentId,       // 콘텐츠 ID
        Long totalViews,      // 해당 날짜의 총 조회수 (count)
        Long totalWatchTime,  // 해당 날짜의 총 시청시간 합계(초)
        LocalDate watchedDate // 시청 날짜
) {
}
