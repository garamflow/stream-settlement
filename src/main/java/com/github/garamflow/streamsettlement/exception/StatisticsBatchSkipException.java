package com.github.garamflow.streamsettlement.exception;

public class StatisticsBatchSkipException extends RuntimeException {
    public StatisticsBatchSkipException(String message) {
        super(message);
    }

    public StatisticsBatchSkipException(String message, Throwable cause) {
        super(message, cause);
    }
}
