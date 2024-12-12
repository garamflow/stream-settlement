package com.github.garamflow.streamsettlement.controller.stream;

import com.github.garamflow.streamsettlement.controller.dto.stream.ContentPlaybackInfo;
import com.github.garamflow.streamsettlement.controller.dto.stream.request.StreamingEndRequest;
import com.github.garamflow.streamsettlement.controller.dto.stream.response.StreamingStartResponse;
import com.github.garamflow.streamsettlement.redis.dto.AbusingKey;
import com.github.garamflow.streamsettlement.service.cache.DailyStreamingContentCacheService;
import com.github.garamflow.streamsettlement.service.cache.ViewCountCacheServiceImpl;
import com.github.garamflow.streamsettlement.service.stream.StreamingServiceImpl;
import com.github.garamflow.streamsettlement.service.stream.ViewAbusingCacheService;
import com.github.garamflow.streamsettlement.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/streaming")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingServiceImpl streamingServiceImpl;
    private final DailyStreamingContentCacheService dailyStreamingContentCacheService; // 여기를 수정
    private final ViewAbusingCacheService viewAbusingCacheService;
    private final ViewCountCacheServiceImpl viewCountCacheServiceImpl;

    @GetMapping("/contents/{contentId}")
    public ResponseEntity<StreamingStartResponse> startStreaming(
            HttpServletRequest request,
            @RequestParam @Min(1) Long userId,
            @PathVariable @Min(1) Long contentId
    ) {

        try {
            ContentPlaybackInfo playbackInfo = streamingServiceImpl.startPlayback(userId, contentId);

            AbusingKey abusingKey = new AbusingKey(
                    userId,
                    contentId,
                    playbackInfo.creatorId(),
                    IpUtil.getClientIp(request)
            );

            if (!viewAbusingCacheService.isAbusing(abusingKey)) {
                dailyStreamingContentCacheService.setContentId(contentId);
                viewCountCacheServiceImpl.incrementViewCount(contentId);
                viewAbusingCacheService.setAbusing(abusingKey);
            }

            StreamingStartResponse response = StreamingStartResponse.builder()
                    .contentPostId(playbackInfo.contentPostId())
                    .creatorId(playbackInfo.creatorId())
                    .title(playbackInfo.title())
                    .videoUrl(playbackInfo.videoUrl())
                    .lastViewedPosition(playbackInfo.lastViewedPosition())
                    .totalDuration(playbackInfo.totalDuration())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Streaming start failed - userId: {}, contentId: {}", userId, contentId, e);
            throw e;
        }
    }

    @PostMapping("/contents/{contentId}")
    public ResponseEntity<Void> endStreaming(
            @RequestParam @Min(1) Long userId,
            @PathVariable @Min(1) Long contentId,
            @Valid @RequestBody StreamingEndRequest request
    ) {
        try {
            streamingServiceImpl.endPlayback(
                    userId,
                    contentId,
                    request.finalPosition(),
                    request.endType()
            );

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Streaming end failed - userId: {}, contentPostId: {}", userId, contentId, e);
            throw e;
        }
    }
}