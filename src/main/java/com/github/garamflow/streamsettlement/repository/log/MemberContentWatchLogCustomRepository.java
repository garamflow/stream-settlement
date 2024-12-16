package com.github.garamflow.streamsettlement.repository.log;

import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;

import java.util.List;

public interface MemberContentWatchLogCustomRepository {
    void bulkInsertLogs(List<MemberContentWatchLog> logs);
}
