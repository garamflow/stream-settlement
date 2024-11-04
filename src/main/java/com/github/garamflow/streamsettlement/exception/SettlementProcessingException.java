package com.github.garamflow.streamsettlement.exception;

/**
 * 정산 처리 과정 중 발생하는 일반적인 예외를 처리하기 위한 예외 클래스
 * 데이터 처리, 엔티티 조회 실패 등 정산 프로세스 전반에서 발생할 수 있는 예외 상황 처리
 */
public class SettlementProcessingException extends RuntimeException {
    public SettlementProcessingException(String message) {
        super(message);
    }

    public SettlementProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
