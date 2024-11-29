package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class DailyLogProcessor implements ItemProcessor<DailyMemberViewLog, ContentStatistics> {

    @Override
    public ContentStatistics process(@NonNull DailyMemberViewLog dailyLog) {
        // 상태 확인
        if (dailyLog.getStatus() != StreamingStatus.COMPLETED) {
            log.warn("Skipping log with status {} for ID: {}", dailyLog.getStatus(), dailyLog.getId());
            return null;
        }

        // ContentPost null 체크
        ContentPost contentPost = dailyLog.getContentPost();
        if (contentPost == null) {
            log.error("ContentPost is null for DailyMemberViewLog ID: {}", dailyLog.getId());
            throw new IllegalStateException("ContentPost cannot be null.");
        }

        // Member null 체크
        if (dailyLog.getMember() == null) {
            log.error("Member is null for DailyMemberViewLog ID: {}", dailyLog.getId());
            throw new IllegalStateException("DailyMemberViewLog contains null fields: member is null");
        }

        // ContentStatistics 생성
        log.debug("Processing log ID: {}", dailyLog.getId());
        return new ContentStatistics.Builder()
                .contentPost(contentPost)
                .statisticsDate(dailyLog.getLogDate())
                .period(StatisticsPeriod.DAILY)
                .viewCount(1L)
                .watchTime((long) dailyLog.getLastViewedPosition())
                .accumulatedViews(contentPost.getTotalViews())
                .build();
    }
}

