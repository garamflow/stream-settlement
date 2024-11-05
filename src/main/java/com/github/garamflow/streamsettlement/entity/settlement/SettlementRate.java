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
@Table(name = "settlement_rate")
public class SettlementRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="settlement_rate_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type")
    private SettlementType settlementType;

    @Column(name = "min_views")
    private Long minViews;
    @Column(name = "max_views")
    private Long maxViews;

    @Column(name = "rate")
    private BigDecimal rate;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    public boolean isApplicable(LocalDateTime dateTime) {
        return (appliedAt == null || !appliedAt.isAfter(dateTime)) &&
                (expiredAt == null || !dateTime.isAfter(expiredAt));
    }
}
