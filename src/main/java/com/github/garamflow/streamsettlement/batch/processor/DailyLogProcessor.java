package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
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
import java.util.*;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogProcessor implements ItemProcessor<MemberContentWatchLog, List<ContentStatistics>> {

    private final ContentPostRepository contentPostRepository;
    private final ContentStatisticsRepository contentStatisticsRepository;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private Map<Long, ContentStatistics> statisticsMap = new HashMap<>();

    @Override
    public List<ContentStatistics> process(@NonNull MemberContentWatchLog watchLog) {
        if (!isValid(watchLog)) {
            return null;
        }

        ContentPost contentPost = contentPostRepository.findById(watchLog.getContentPostId())
                .orElseThrow(() -> new NoSuchElementException("ContentPost not found"));

        ContentStatistics statistics = statisticsMap.computeIfAbsent(
                contentPost.getId(),
                id -> {
                    // findLatestByContentPostId 대신 기존 메서드 활용
                    Optional<ContentStatistics> latest = contentStatisticsRepository
                            .findByContentPost_IdAndPeriodAndStatisticsDate(
                                    contentPost.getId(),
                                    StatisticsPeriod.DAILY,
                                    targetDate
                            );

                    return ContentStatistics.customBuilder()
                            .contentPost(contentPost)
                            .statisticsDate(targetDate)
                            .period(StatisticsPeriod.DAILY)
                            .viewCount(0L)
                            .watchTime(0L)
                            .accumulatedViews(latest.map(ContentStatistics::getAccumulatedViews)
                                    .orElse(contentPost.getTotalViews()))
                            .build();
                }
        );

        statistics.incrementViewCount();
        statistics.addWatchTime(watchLog.getTotalPlaybackTime());

        return List.of(statistics);
    }

    private boolean isValid(MemberContentWatchLog watchLog) {
        if (watchLog == null) {
            log.warn("WatchLog is null");
            return false;
        }
        if (watchLog.getStreamingStatus() != StreamingStatus.COMPLETED) {
            log.warn("StreamingStatus is not COMPLETED: {}", watchLog.getStreamingStatus());
            return false;
        }
        if (watchLog.getLastPlaybackPosition() <= 0) {
            log.warn("LastPlaybackPosition is not positive: {}", watchLog.getLastPlaybackPosition());
            return false;
        }
        if (!watchLog.isTotalPlaybackTimeValid()) {
            log.warn("TotalPlaybackTime is invalid: {}", watchLog.getTotalPlaybackTime());
            return false;
        }
        if (!watchLog.isWatchDateValid()) {
            log.warn("WatchDate is invalid: {}", watchLog.getWatchedDate());
            return false;
        }
        return true;
    }
}