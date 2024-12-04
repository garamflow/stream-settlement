package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.util.StatisticsUtil;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@StepScope
public class DailyLogProcessor implements ItemProcessor<DailyMemberViewLog, List<ContentStatistics>> {

  @Override
  public List<ContentStatistics> process(@NonNull DailyMemberViewLog dailyLog) {
      if (dailyLog.getStatus() != StreamingStatus.COMPLETED) {
          log.warn("Skipping log with status {} for ID: {}", dailyLog.getStatus(), dailyLog.getId());
          return null;
      }

      if (dailyLog.getMember() == null) {
          throw new IllegalStateException("member is null");
      }
      if (dailyLog.getContentPost() == null) {
          throw new IllegalStateException("contentPost is null");
      }

      return StatisticsUtil.createStatistics(
              dailyLog.getContentPost(),
              dailyLog.getLogDate(),
              dailyLog.getLastViewedPosition()
      );
  }
}

