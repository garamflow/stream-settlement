package com.github.garamflow.streamsettlement.controller.statistics;

import com.github.garamflow.streamsettlement.controller.dto.stream.response.ContentStatisticsResponse;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.service.statistics.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/top-views")
    public ResponseEntity<List<ContentStatisticsResponse>> getTopByViews(
            @RequestParam StatisticsPeriod period
    ) {

        List<ContentStatisticsResponse> responses = statisticsService
                .getTop5Views(period);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/top-watch-time")
    public ResponseEntity<List<ContentStatisticsResponse>> getTopByWatchTimes(
            @RequestParam StatisticsPeriod period
    ) {

        List<ContentStatisticsResponse> responses = statisticsService
                .getTop5WatchTime(period);

        return ResponseEntity.ok(responses);
    }
}
