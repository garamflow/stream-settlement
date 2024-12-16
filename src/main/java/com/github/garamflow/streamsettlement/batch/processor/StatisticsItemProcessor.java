package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class StatisticsItemProcessor implements ItemProcessor<CumulativeStatisticsDto, ContentStatistics> {

    private final ContentPostRepository contentPostRepository;
    private final ContentStatisticsRepository contentStatisticsRepository;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    @Override
    public ContentStatistics process(@NonNull CumulativeStatisticsDto dto) {
        // ContentPost 조회
        ContentPost contentPost = contentPostRepository.findById(dto.contentId())
                .orElseThrow(() -> new NoSuchElementException("ContentPost not found"));

        // 기존 통계 조회
        Optional<ContentStatistics> latest = contentStatisticsRepository
                .findByContentPost_IdAndPeriodAndStatisticsDate(
                        dto.contentId(),
                        StatisticsPeriod.DAILY,
                        targetDate
                );

        long accumulatedViews = latest.map(ContentStatistics::getAccumulatedViews)
                .orElse(contentPost.getTotalViews()); // 기존 통계 없으면 contentPost.totalViews() 사용

        return ContentStatistics.existingBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(dto.totalViews())           // 이미 집계된 일일 조회수
                .watchTime(dto.totalWatchTime())       // 이미 집계된 일일 재생시간
                .accumulatedViews(accumulatedViews)    // 누적 뷰
                .build();
    }
}

