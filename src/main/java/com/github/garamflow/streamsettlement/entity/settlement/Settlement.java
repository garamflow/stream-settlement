package com.github.garamflow.streamsettlement.entity.settlement;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_settlement_content_date",
                        columnNames = {"content_post_id", "settlement_date"}
                )
        },
        indexes = {
                @Index(name = "idx_settlement_id_date", 
                       columnList = "settlement_id, settlement_date"),
        })
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    @Column(name = "content_post_id", nullable = false)
    private Long contentPostId;

    @Column(name = "content_revenue", nullable = false)
    private Long contentRevenue;

    @Column(name = "ad_revenue", nullable = false)
    private Long adRevenue;

    @Column(name = "total_content_revenue", nullable = false)
    private Long totalContentRevenue;

    @Column(name = "total_ad_revenue", nullable = false)
    private Long totalAdRevenue;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status = SettlementStatus.CALCULATED;

    @Builder(builderMethodName = "createBuilder")
    private Settlement(Long contentPostId,
                      Long contentRevenue,
                      Long adRevenue,
                      Long totalContentRevenue,
                      Long totalAdRevenue,
                      LocalDate settlementDate,
                      SettlementStatus status) {
        this.contentPostId = contentPostId;
        this.contentRevenue = contentRevenue;
        this.adRevenue = adRevenue;
        this.totalContentRevenue = totalContentRevenue;
        this.totalAdRevenue = totalAdRevenue;
        this.settlementDate = settlementDate;
        this.status = status != null ? status : SettlementStatus.CALCULATED;
    }

    @Builder(builderMethodName = "existingBuilder")
    private Settlement(Long id,
                      Long contentPostId,
                      Long contentRevenue,
                      Long adRevenue,
                      Long totalContentRevenue,
                      Long totalAdRevenue,
                      LocalDate settlementDate,
                      LocalDateTime createdAt,
                      SettlementStatus status) {
        this.id = id;
        this.contentPostId = contentPostId;
        this.contentRevenue = contentRevenue;
        this.adRevenue = adRevenue;
        this.totalContentRevenue = totalContentRevenue;
        this.totalAdRevenue = totalAdRevenue;
        this.settlementDate = settlementDate;
        this.createdAt = createdAt;
        this.status = status;
    }
}
