package com.github.garamflow.streamsettlement.domain;

public enum AdRevenueRange {
    TIER_1(0L, 99_999L, 10.0),
    TIER_2(100_000L, 500_000L, 12.0),
    TIER_3(500_001L, 1_000_000L, 15.0),
    TIER_4(1_000_001L, Long.MAX_VALUE, 20.0);

    private final long minViews;
    private final long maxViews;
    private final double pricePerView;

    AdRevenueRange(long minViews, long maxViews, double pricePerView) {
        this.minViews = minViews;
        this.maxViews = maxViews;
        this.pricePerView = pricePerView;
    }

    public static long calculateRevenueByViews(long totalViews) {
        long remainingViews = totalViews;
        long totalRevenue = 0;

        for (AdRevenueRange range : values()) {
            if (remainingViews <= 0) {
                break;
            }

            long viewsInRange = Math.min(
                    remainingViews,
                    range.maxViews - range.minViews + 1
            );

            totalRevenue += (long) (viewsInRange * range.pricePerView);
            remainingViews -= viewsInRange;
        }

        return totalRevenue;
    }
}
