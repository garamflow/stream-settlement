package com.github.garamflow.streamsettlement.entity.settlement;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SettlementType settlementType;

    private Long minViews;
    private Long maxViews;

    private BigDecimal rate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;
    private LocalDateTime expiredAt;

    public boolean isApplicable(LocalDateTime dateTime) {
        return (appliedAt == null || !appliedAt.isAfter(dateTime)) &&
                (expiredAt == null || !dateTime.isAfter(expiredAt));
    }
}
