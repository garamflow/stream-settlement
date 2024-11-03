package com.github.garamflow.streamsettlement.service.stream;

import com.github.garamflow.streamsettlement.controller.dto.stream.StreamingEndType;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.stream.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class StreamingService {

    private final ContentPostRepository contentPostRepository;
    private final DailyMemberViewLogRepository dailyMemberViewLogRepository;
    private final MemberRepository memberRepository;

    public DailyMemberViewLog startPlayback(Long contentPostId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("Member not found"));

        ContentPost contentPost = contentPostRepository.findById(contentPostId)
                .orElseThrow(() -> new NoSuchElementException("ContentPost not found"));

        DailyMemberViewLog viewLog = dailyMemberViewLogRepository
                .findByMemberIdAndContentPostId(memberId, contentPostId)
                .map(previousLog ->
                        DailyMemberViewLog.createContinueLog(
                                member,
                                contentPost,
                                previousLog.getLastViewedPosition(),
                                previousLog.getLastAdViewCount()
                        )
                )
                .orElseGet(() -> {
                    contentPost.incrementTotalViews();
                    return DailyMemberViewLog.createNewLog(member, contentPost);
                });

        // Todo 캐시로 저장할 예정
        return dailyMemberViewLogRepository.save(viewLog);
    }

    // 재생 위치 업데이트
    public StreamingStatus updatePlaybackPosition(Long memberId, Long contentPostId, Integer positionInSeconds) {
        DailyMemberViewLog viewLog = dailyMemberViewLogRepository
                .findByMemberIdAndContentPostId(memberId, contentPostId)
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

    public void endPlayback(Long memberId, Long contentPostId,
                            Integer finalPosition, StreamingEndType endType) {

        DailyMemberViewLog viewLog = dailyMemberViewLogRepository
                .findByMemberIdAndContentPostId(memberId, contentPostId)
                .orElseThrow(() -> new NoSuchElementException("Log not found"));

        StreamingStatus newStatus = switch (endType) {
            case COMPLETE -> StreamingStatus.COMPLETED;
            case PAUSE -> StreamingStatus.PAUSED;
            case STOP -> StreamingStatus.STOPPED;
        };

        viewLog.updatePosition(finalPosition, newStatus);
    }
}
