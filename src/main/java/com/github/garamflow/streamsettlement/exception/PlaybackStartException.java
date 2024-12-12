package com.github.garamflow.streamsettlement.exception;

// 커스텀 예외
public class PlaybackStartException extends RuntimeException {
    public PlaybackStartException(String message, Throwable cause) {
        super(message, cause);
    }
}
