package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogProcessor implements ItemProcessor<MemberContentWatchLog, List<ContentStatistics>> {

    private final ContentPostRepository contentPostRepository;

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    @Override
    public List<ContentStatistics> process(@NonNull MemberContentWatchLog watchLog) {
        if (!isValid(watchLog)) {
            log.warn("Invalid watchLog: {}", watchLog);
            return null;
        }

        ContentPost contentPost = contentPostRepository.findById(watchLog.getContentPostId())
                .orElseThrow(() -> new NoSuchElementException("ContentPost not found"));

        return List.of(ContentStatistics.builder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(1L)
                .watchTime(watchLog.getTotalPlaybackTime())
                .accumulatedViews(contentPost.getTotalViews())
                .build());
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