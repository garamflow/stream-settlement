package com.github.garamflow.streamsettlement.entity.settlement;

public enum SettlementStatus {
    CALCULATED("정산 계산 완료"),
    COMPLETED("정산 처리 완료"),
    FAILED("정산 실패");

    private final String description;

    SettlementStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
