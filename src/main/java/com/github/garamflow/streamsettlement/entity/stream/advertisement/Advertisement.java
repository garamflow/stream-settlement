package com.github.garamflow.streamsettlement.entity.stream.advertisement;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "advertisement")
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "advertisement_id")
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "price_per_view")
    private Long pricePerView;

    @Column(name = "total_views")
    private Long totalViews = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "advertisement")
    private List<AdvertisementContentPost> contentPosts = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advertiser_id", nullable = false)
    private Member advertiser;

    @Builder(builderMethodName = "createBuilder")
    private Advertisement(String title, 
                        String description, 
                        Long pricePerView, 
                        Member advertiser) {
        this.title = title;
        this.description = description;
        this.pricePerView = pricePerView;
        this.advertiser = advertiser;
        this.totalViews = 0L;
        this.contentPosts = new ArrayList<>();
    }

    @Builder(builderMethodName = "existingBuilder")
    private Advertisement(Long id,
                        String title,
                        String description,
                        Long pricePerView,
                        Long totalViews,
                        LocalDateTime createdAt,
                        Member advertiser,
                        List<AdvertisementContentPost> contentPosts) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.pricePerView = pricePerView;
        this.totalViews = totalViews;
        this.createdAt = createdAt;
        this.advertiser = advertiser;
        this.contentPosts = contentPosts != null ? contentPosts : new ArrayList<>();
    }

    public void incrementViews(int count) {
        this.totalViews += count;
    }
}
