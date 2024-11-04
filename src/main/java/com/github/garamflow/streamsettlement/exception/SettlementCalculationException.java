package com.github.garamflow.streamsettlement.exception;

/**
 * 정산금액 계산 중 발생하는 예외를 처리하기 위한 예외 클래스
 * 주로 금액 계산 시 발생하는 산술 오버플로우나 잘못된 계산식 적용 등의 상황에서 발생
 */
public class SettlementCalculationException extends RuntimeException {
    public SettlementCalculationException(String message) {
        super(message);
    }

    public SettlementCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
