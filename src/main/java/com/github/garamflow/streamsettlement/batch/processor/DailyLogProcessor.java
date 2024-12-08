package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.validator.DailyMemberViewLogValidator;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.service.statistics.ContentStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyLogProcessor implements ItemProcessor<DailyMemberViewLog, List<ContentStatistics>> {

    private final ContentStatisticsService contentStatisticsService;
    private final DailyMemberViewLogValidator validator;

    @Override
    public List<ContentStatistics> process(@NonNull DailyMemberViewLog dailyLog) {
        if (!validator.isValid(dailyLog)) {
            return null;
        }

        return contentStatisticsService.createDailyStatistics(
                dailyLog.getContentPost(),
                dailyLog.getLogDate(),
                dailyLog.getLastViewedPosition()
        );
    }
}

