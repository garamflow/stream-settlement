package com.github.garamflow.streamsettlement.domain;

public interface BaseRevenueRange {
    long getMinViews();

    long getMaxViews();

    double getPricePerView();

    default long calculateRevenueByViews(RevenueCalculator[] ranges, long totalViews) {
        long remainingViews = totalViews;
        long totalRevenue = 0;

        for (RevenueCalculator range : ranges) {
            if (remainingViews <= 0) break;

            BaseRevenueRange baseRange = (BaseRevenueRange) range;
            long viewsInRange = Math.min(
                    remainingViews,
                    baseRange.getMaxViews() - baseRange.getMinViews() + 1
            );

            totalRevenue += (long) (viewsInRange * baseRange.getPricePerView());
            remainingViews -= viewsInRange;
        }

        return totalRevenue;
    }
}
