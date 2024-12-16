package com.github.garamflow.streamsettlement.batch.dto;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.ConstructorExpression;
import javax.annotation.processing.Generated;

/**
 * com.github.garamflow.streamsettlement.batch.dto.QCumulativeStatisticsDto is a Querydsl Projection type for CumulativeStatisticsDto
 */
@Generated("com.querydsl.codegen.DefaultProjectionSerializer")
public class QCumulativeStatisticsDto extends ConstructorExpression<CumulativeStatisticsDto> {

    private static final long serialVersionUID = -945954021L;

    public QCumulativeStatisticsDto(com.querydsl.core.types.Expression<Long> contentId, com.querydsl.core.types.Expression<Long> totalViews, com.querydsl.core.types.Expression<Long> totalWatchTime, com.querydsl.core.types.Expression<Long> accumulatedViews) {
        super(CumulativeStatisticsDto.class, new Class<?>[]{long.class, long.class, long.class, long.class}, contentId, totalViews, totalWatchTime, accumulatedViews);
    }

}

