package com.github.garamflow.streamsettlement.entity.settlement;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_statistics_id")
    private ContentStatistics contentStatistics;

    private LocalDate settlementDate;

    // 통계 데이터에서 가져오는 값들
    private Long dailyViews;               // 해당 일자 조회수
    private Long totalViews;               // 누적 조회수

    // 정산 계산 결과
    private Long contentAmount;            // 컨텐츠 정산금액
    private Long adAmount;                 // 광고 정산금액
    private Long totalAmount;              // 총 정산금액

    @Enumerated(EnumType.STRING)
    private SettlementStatus status;       // 정산 상태

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private Settlement(Builder builder) {
        this.contentStatistics = builder.contentStatistics;
        this.settlementDate = builder.settlementDate;
        this.dailyViews = builder.dailyViews;
        this.totalViews = builder.totalViews;
        this.contentAmount = builder.contentAmount;
        this.adAmount = builder.adAmount;
        this.totalAmount = builder.totalAmount;
        this.status = builder.status;
    }

    public static class Builder {
        private ContentStatistics contentStatistics;
        private LocalDate settlementDate;
        private Long dailyViews;
        private Long totalViews;
        private Long contentAmount;
        private Long adAmount;
        private Long totalAmount;
        private SettlementStatus status;

        public Builder contentStatistics(ContentStatistics contentStatistics) {
            this.contentStatistics = contentStatistics;
            return this;
        }

        public Builder settlementDate(LocalDate settlementDate) {
            this.settlementDate = settlementDate;
            return this;
        }

        public Builder dailyViews(Long dailyViews) {
            this.dailyViews = dailyViews;
            return this;
        }

        public Builder totalViews(Long totalViews) {
            this.totalViews = totalViews;
            return this;
        }

        public Builder contentAmount(Long contentAmount) {
            this.contentAmount = contentAmount;
            return this;
        }

        public Builder adAmount(Long adAmount) {
            this.adAmount = adAmount;
            return this;
        }

        public Builder totalAmount(Long totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder status(SettlementStatus status) {
            this.status = status;
            return this;
        }

        public Settlement build() {
            return new Settlement(this);
        }
    }

}
