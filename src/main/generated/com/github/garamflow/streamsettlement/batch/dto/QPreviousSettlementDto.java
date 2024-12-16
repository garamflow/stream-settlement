package com.github.garamflow.streamsettlement.batch.dto;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.ConstructorExpression;
import javax.annotation.processing.Generated;

/**
 * com.github.garamflow.streamsettlement.batch.dto.QPreviousSettlementDto is a Querydsl Projection type for PreviousSettlementDto
 */
@Generated("com.querydsl.codegen.DefaultProjectionSerializer")
public class QPreviousSettlementDto extends ConstructorExpression<PreviousSettlementDto> {

    private static final long serialVersionUID = 513766993L;

    public QPreviousSettlementDto(com.querydsl.core.types.Expression<Long> contentPostId, com.querydsl.core.types.Expression<Long> previousTotalContentRevenue, com.querydsl.core.types.Expression<Long> previousTotalAdRevenue) {
        super(PreviousSettlementDto.class, new Class<?>[]{long.class, long.class, long.class}, contentPostId, previousTotalContentRevenue, previousTotalAdRevenue);
    }

}

