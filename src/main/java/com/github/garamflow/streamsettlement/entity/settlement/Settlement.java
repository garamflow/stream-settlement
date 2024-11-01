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
    @GeneratedValue(strategy = GenerationType.AUTO)
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

}
