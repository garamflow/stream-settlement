package com.github.garamflow.streamsettlement.domain;

public enum ContentRevenueRange implements BaseRevenueRange, RevenueCalculator {
    TIER_1(0L, 99_999L, 1.0),
    TIER_2(100_000L, 500_000L, 1.1),
    TIER_3(500_001L, 1_000_000L, 1.3),
    TIER_4(1_000_001L, Long.MAX_VALUE, 1.5);

    private final long minViews;
    private final long maxViews;
    private final double pricePerView;

    ContentRevenueRange(long minViews, long maxViews, double pricePerView) {
        this.minViews = minViews;
        this.maxViews = maxViews;
        this.pricePerView = pricePerView;
    }

    @Override
    public long getMinViews() {
        return minViews;
    }

    @Override
    public long getMaxViews() {
        return maxViews;
    }

    @Override
    public double getPricePerView() {
        return pricePerView;
    }

    @Override
    public long calculateRevenueByViews(long totalViews) {
        return calculateTotalRevenue(totalViews);
    }

    public static long calculateTotalRevenue(long totalViews) {
        return TIER_1.calculateRevenueByViews(values(), totalViews);
    }
}