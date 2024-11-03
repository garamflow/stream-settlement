package com.github.garamflow.streamsettlement.controller.stream;

import com.github.garamflow.streamsettlement.controller.dto.stream.request.StreamingEndRequest;
import com.github.garamflow.streamsettlement.controller.dto.stream.request.UpdateStreamingPositionRequest;
import com.github.garamflow.streamsettlement.controller.dto.stream.response.StreamingPositionResponse;
import com.github.garamflow.streamsettlement.controller.dto.stream.response.StreamingStartResponse;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.service.stream.StreamingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/streaming")
public class StreamingController {

    private final StreamingService streamingService;

    @PostMapping("/start")
    public ResponseEntity<StreamingStartResponse> startStreaming(
            @RequestParam Long contentPostId,
            @RequestParam Long memberId
    ) {
        DailyMemberViewLog viewLog = streamingService.startPlayback(contentPostId, memberId);
        ContentPost contentPost = viewLog.getContentPost();

        StreamingStartResponse response = StreamingStartResponse.builder()
                .contentPostId(contentPost.getId())
                .title(contentPost.getTitle())
                .videoUrl(contentPost.getUrl())
                .lastViewedPosition(viewLog.getLastViewedPosition())
                .totalDuration(contentPost.getDuration())
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/position")
    public ResponseEntity<StreamingPositionResponse> updatePosition(
            @Valid @RequestBody UpdateStreamingPositionRequest request) {

        StreamingStatus status = streamingService.updatePlaybackPosition(
                request.memberId(),
                request.contentPostId(),
                request.positionInSeconds()
        );

        StreamingPositionResponse response = new StreamingPositionResponse(
                status,
                status == StreamingStatus.COMPLETED || status == StreamingStatus.STOPPED,
                getMessageForStatus(status)
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/end")
    public ResponseEntity<Void> endStreaming(
            @Valid @RequestBody StreamingEndRequest request
    ) {
        streamingService.endPlayback(
                request.memberId(),
                request.contentPostId(),
                request.finalPosition(),
                request.endType()
        );
        return ResponseEntity.ok().build();
    }

    private String getMessageForStatus(StreamingStatus status) {
        return switch (status) {
            case IN_PROGRESS -> "Streaming in progress";
            case COMPLETED -> "Streaming completed";
            case STOPPED -> "Streaming stopped";
            case PAUSED -> "Streaming paused";
        };
    }
}
