package com.github.garamflow.streamsettlement.controller.statistics;

import com.github.garamflow.streamsettlement.controller.dto.statistics.ContentStatisticsResponse;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.service.statistics.ContentStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class ContentStatisticsController {

    private final ContentStatisticsService contentStatisticsService;

    // 기존 일간/주간/월간/연간 등 단일 targetDate 기반 Top5(조회수 기준)
    @GetMapping("/top-views")
    public ResponseEntity<List<ContentStatisticsResponse>> getTopByViews(
            @RequestParam StatisticsPeriod period
    ) {
        List<ContentStatisticsResponse> responses = contentStatisticsService.getTop5Views(period);
        return ResponseEntity.ok(responses);
    }

    // 기존 단일 targetDate 기반 Top5(시청시간 기준)
    @GetMapping("/top-watch-time")
    public ResponseEntity<List<ContentStatisticsResponse>> getTopByWatchTimes(
            @RequestParam StatisticsPeriod period
    ) {
        List<ContentStatisticsResponse> responses = contentStatisticsService.getTop5WatchTime(period);
        return ResponseEntity.ok(responses);
    }

    // 추가: 기간(From~To) 기반 조회수 Top5 조회
    @GetMapping("/top-views-range")
    public ResponseEntity<List<ContentStatisticsResponse>> getTopViewsInRange(
            @RequestParam StatisticsPeriod period,
            @RequestParam("startDate") String startDateStr,
            @RequestParam("endDate") String endDateStr
    ) {
        // startDate, endDate 파싱 (yyyy-MM-dd 형식 가정)
        // 실제 코드에서는 예외 처리를 해주는 것이 좋음
        var startDate = LocalDate.parse(startDateStr);
        var endDate = LocalDate.parse(endDateStr);

        List<ContentStatisticsResponse> responses = contentStatisticsService.getTop5ViewsBetween(period, startDate, endDate);
        return ResponseEntity.ok(responses);
    }

    // 추가: 기간(From~To) 기반 시청시간 Top5 조회
    @GetMapping("/top-watch-time-range")
    public ResponseEntity<List<ContentStatisticsResponse>> getTopWatchTimeInRange(
            @RequestParam StatisticsPeriod period,
            @RequestParam("startDate") String startDateStr,
            @RequestParam("endDate") String endDateStr
    ) {
        var startDate = LocalDate.parse(startDateStr);
        var endDate = LocalDate.parse(endDateStr);

        List<ContentStatisticsResponse> responses = contentStatisticsService.getTop5WatchTimeBetween(period, startDate, endDate);
        return ResponseEntity.ok(responses);
    }

    // 추가: 기간(From~To) 통계 조회
    @GetMapping("/range")
    public ResponseEntity<List<ContentStatisticsResponse>> getStatisticsInRange(
            @RequestParam StatisticsPeriod period,
            @RequestParam("startDate") String startDateStr,
            @RequestParam("endDate") String endDateStr
    ) {
        var startDate = LocalDate.parse(startDateStr);
        var endDate = LocalDate.parse(endDateStr);

        var statisticsList = contentStatisticsService.getStatisticsBetween(period, startDate, endDate);
        var responses = statisticsList.stream()
                .map(ContentStatisticsResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

}

