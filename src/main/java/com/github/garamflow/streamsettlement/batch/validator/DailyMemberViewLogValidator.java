package com.github.garamflow.streamsettlement.batch.validator;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyMemberViewLogValidator {

    public boolean isValid(DailyMemberViewLog dailyLog) {
        if (dailyLog == null) {
            log.error("DailyMemberViewLog is null");
            return false;
        }

        if (dailyLog.getStatus() != StreamingStatus.COMPLETED) {
            log.warn("Skipping log with status {} for ID: {}", dailyLog.getStatus(), dailyLog.getId());
            return false;
        }

        if (dailyLog.getMember() == null) {
            log.error("Member is null for log ID: {}", dailyLog.getId());
            return false;
        }

        if (dailyLog.getContentPost() == null) {
            log.error("ContentPost is null for log ID: {}", dailyLog.getId());
            return false;
        }

        if (dailyLog.getLastViewedPosition() <= 0) {
            log.warn("Invalid lastViewedPosition for log ID: {}", dailyLog.getId());
            return false;
        }

        return true;
    }
} 