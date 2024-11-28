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
        // Null 체크 및 상태 확인
        if (dailyLog.getStatus() != StreamingStatus.COMPLETED) {
            return null;
        }

        // ContentPost 를 변수로 분리
        ContentPost contentPost = dailyLog.getContentPost();
        if (contentPost == null) {
            throw new IllegalStateException("ContentPost is null for DailyMemberViewLog with ID: " + dailyLog.getId());
        }

        // 필드 유효성 검증
        if (dailyLog.getMember() == null || dailyLog.getLogDate() == null) {
            throw new IllegalStateException("DailyMemberViewLog contains null fields");
        }

        // ContentStatistics 생성
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

