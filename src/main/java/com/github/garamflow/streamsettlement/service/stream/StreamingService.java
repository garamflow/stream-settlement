package com.github.garamflow.streamsettlement.service.stream;

import com.github.garamflow.streamsettlement.controller.dto.stream.StreamingEndType;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyUserViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.user.User;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.stream.DailyUserViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class StreamingService {

    private final ContentPostRepository contentPostRepository;
    private final DailyUserViewLogRepository dailyUserViewLogRepository;
    private final UserRepository userRepository;

    public DailyUserViewLog startPlayback(Long contentPostId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        ContentPost contentPost = contentPostRepository.findById(contentPostId)
                .orElseThrow(() -> new NoSuchElementException("ContentPost not found"));

        DailyUserViewLog viewLog = dailyUserViewLogRepository
                .findByUserIdAndContentPostId(userId, contentPostId)
                .map(previousLog ->
                        DailyUserViewLog.createContinueLog(
                                user,
                                contentPost,
                                previousLog.getLastViewedPosition(),
                                previousLog.getLastAdViewCount()
                        )
                )
                .orElseGet(() -> {
                    contentPost.incrementTotalViews();
                    return DailyUserViewLog.createNewLog(user, contentPost);
                });

        // Todo 캐시로 저장할 예정
        return dailyUserViewLogRepository.save(viewLog);
    }

    // 재생 위치 업데이트
    public StreamingStatus updatePlaybackPosition(Long userId, Long contentPostId, Integer positionInSeconds) {
        DailyUserViewLog viewLog = dailyUserViewLogRepository
                .findByUserIdAndContentPostId(userId, contentPostId)
                .orElseThrow(() -> new NoSuchElementException("Log not found"));

        // 현재 상태 확인
        StreamingStatus currentStatus = viewLog.getStatus();

        // 이미 종료된 상태면 상태 반환
        if (currentStatus == StreamingStatus.COMPLETED ||
                currentStatus == StreamingStatus.STOPPED) {
            return currentStatus;
        }

        // 일시정지 상태면 그대로 반환
        if (currentStatus == StreamingStatus.PAUSED) {
            return currentStatus;
        }

        // 정상 재생중
        viewLog.updatePosition(positionInSeconds, StreamingStatus.IN_PROGRESS);
        return StreamingStatus.IN_PROGRESS;
    }

    public void endPlayback(Long userId, Long contentPostId,
                            Integer finalPosition, StreamingEndType endType) {

        DailyUserViewLog viewLog = dailyUserViewLogRepository
                .findByUserIdAndContentPostId(userId, contentPostId)
                .orElseThrow(() -> new NoSuchElementException("Log not found"));

        StreamingStatus newStatus = switch (endType) {
            case COMPLETE -> StreamingStatus.COMPLETED;
            case PAUSE -> StreamingStatus.PAUSED;
            case STOP -> StreamingStatus.STOPPED;
        };

        viewLog.updatePosition(finalPosition, newStatus);
    }
}
