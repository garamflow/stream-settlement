package com.github.garamflow.streamsettlement.entity.settlement;

public enum SettlementType {
    CONTENT("컨텐츠 수익", 0.4, 0.5, 0.6),
    ADVERTISEMENT("광고 수익", 0.3, 0.4, 0.5);

    private final String description;
    private final double tier1Rate;  // 0-1000 views
    private final double tier2Rate;  // 1001-5000 views
    private final double tier3Rate;  // 5001+ views

    SettlementType(String description, double tier1Rate, double tier2Rate, double tier3Rate) {
        this.description = description;
        this.tier1Rate = tier1Rate;
        this.tier2Rate = tier2Rate;
        this.tier3Rate = tier3Rate;
    }

    public double getRate(long views) {
        if (views <= 1000) return tier1Rate;
        if (views <= 5000) return tier2Rate;
        return tier3Rate;
    }

    public String getDescription() {
        return description;
    }
}
