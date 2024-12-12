package com.github.garamflow.streamsettlement.service.stream;

import com.github.garamflow.streamsettlement.controller.dto.stream.ContentPlaybackInfo;
import com.github.garamflow.streamsettlement.controller.dto.stream.StreamingEndType;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyWatchedContent;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.exception.PlaybackStartException;
import com.github.garamflow.streamsettlement.redis.dto.AbusingKey;
import com.github.garamflow.streamsettlement.repository.log.DailyWatchedContentRepository;
import com.github.garamflow.streamsettlement.repository.log.MemberContentWatchLogRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.service.cache.DailyStreamingContentCacheService;
import com.github.garamflow.streamsettlement.service.cache.ViewCountCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class StreamingServiceImpl implements StreamingService {
    private final DailyWatchedContentRepository dailyWatchedContentRepository;
    private final MemberContentWatchLogRepository memberContentWatchLogRepository;
    private final ContentPostRepository contentPostRepository;
    private final ViewCountCacheService viewCountCacheService;
    private final ViewAbusingCacheService viewAbusingCacheService;
    private final DailyStreamingContentCacheService dailyStreamingContentCacheService;

    @Override
    public ContentPlaybackInfo startPlayback(Long memberId, Long contentId) {
        ContentPost contentPost = contentPostRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Content not found"));

        if (contentPost.getMember() == null) {
            throw new IllegalStateException("Content creator not found");
        }

        try {
            // 어뷰징 체크 및 조회수 증가
            AbusingKey abusingKey = AbusingKey.of(memberId, contentId, contentPost.getMember().getId(), "127.0.0.1");
            if (!viewAbusingCacheService.isAbusing(abusingKey)) {
                // Redis 에 조회수 증가
                viewCountCacheService.incrementViewCount(contentId);
                viewAbusingCacheService.recordView(abusingKey);

                // DB 에도 실시간으로 조회수 증가
                contentPost.incrementTotalViews();
                contentPostRepository.save(contentPost);
            }

            // 일일 시청 컨텐츠 기록
            if (!dailyStreamingContentCacheService.isExistContentId(contentId)) {
                dailyStreamingContentCacheService.setContentId(contentId);
            }

            recordContentWatch(memberId, contentId, LocalDate.now());

            ContentPlayback contentPlayback = new ContentPlayback(
                    contentPost.getId(),
                    contentPost.getMember().getId(),
                    contentPost.getTitle(),
                    contentPost.getUrl(),
                    getLastViewedPosition(memberId, contentId),
                    contentPost.getDuration()
            );

            return ContentPlaybackInfo.fromDomain(contentPlayback);
        } catch (Exception e) {
            log.error("Failed to start playback for content {} by member {}", contentId, memberId, e);
            throw new PlaybackStartException("Failed to start playback", e);
        }
    }

    @Override
    public StreamingStatus updatePlaybackPosition(Long memberId, Long contentId, Long positionInSeconds) {
        var watchLog = memberContentWatchLogRepository
                .findByMemberIdAndContentPostId(memberId, contentId)
                .orElseThrow(() -> new IllegalArgumentException("Watch log not found"));

        if (watchLog.getStreamingStatus().isFinished()) {
            return watchLog.getStreamingStatus();
        }

        watchLog.updatePlaybackPosition(positionInSeconds);
        return StreamingStatus.IN_PROGRESS;
    }

    public void endPlayback(Long memberId, Long contentId, Long finalPosition, StreamingEndType endType) {
        var watchLog = memberContentWatchLogRepository
                .findByMemberIdAndContentPostId(memberId, contentId)
                .orElseThrow(() -> new IllegalArgumentException("Watch log not found"));

        StreamingStatus newStatus = switch (endType) {
            case COMPLETE -> StreamingStatus.COMPLETED;
            case PAUSE -> StreamingStatus.PAUSED;
            case STOP -> StreamingStatus.STOPPED;
        };

        watchLog.updatePlaybackPosition(finalPosition);
        watchLog.updateStatus(newStatus);

        // 일일 시청 기록 업데이트
        updateDailyWatchedContent(contentId, newStatus);
    }

    public void recordContentWatch(Long memberId, Long contentId, LocalDate watchedDate) {
        // 1. DailyWatchedContent 기록 (중복 체크 포함)
        if (!dailyWatchedContentRepository.existsByContentPostIdAndWatchedDate(contentId, watchedDate)) {
            dailyWatchedContentRepository.save(new DailyWatchedContent(contentId, watchedDate));
        }

        // 2. MemberContentWatchLog 기록/업데이트
        memberContentWatchLogRepository.findByMemberIdAndContentPostId(memberId, contentId)
                .ifPresentOrElse(
                        log -> log.updateStatus(StreamingStatus.IN_PROGRESS),
                        () -> memberContentWatchLogRepository.save(
                                MemberContentWatchLog.customBuilder()
                                        .memberId(memberId)
                                        .contentPostId(contentId)
                                        .watchedDate(watchedDate)
                                        .build()
                        )
                );
    }

    private Long getLastViewedPosition(Long memberId, Long contentId) {
        return memberContentWatchLogRepository
                .findByMemberIdAndContentPostId(memberId, contentId)
                .map(MemberContentWatchLog::getLastPlaybackPosition)
                .orElse(0L);
    }

    // 현재 사용되지 않는 메서드입니다. endPlayback 에서 호출되지만 실제 로직이 구현되어 있지 않습니다.
    // TODO: 이 메서드를 제거하거나 필요한 로직을 구현해야 합니다.
    private void updateDailyWatchedContent(Long contentId, StreamingStatus newStatus) {
        dailyWatchedContentRepository
                .findFirstByContentPostIdAndWatchedDateOrderByIdDesc(contentId, LocalDate.now())
                .ifPresent(dailyContent -> {
                    // 상태 업데이트 로직이 필요한 경우 여기에 추가
                });
    }
}
