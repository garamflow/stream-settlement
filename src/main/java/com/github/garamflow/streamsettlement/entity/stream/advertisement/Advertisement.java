package com.github.garamflow.streamsettlement.entity.stream.advertisement;

import com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
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

    public static Builder builder() {
      return new Builder();
  }

  @Getter
  public static class Builder {
      private String title;
      private String description;
      private Long pricePerView;

      public Builder title(String title) {
          this.title = title;
          return this;
      }

      public Builder description(String description) {
          this.description = description;
          return this;
      }

      public Builder pricePerView(Long pricePerView) {
          this.pricePerView = pricePerView;
          return this;
      }

      public Advertisement build() {
          Advertisement advertisement = new Advertisement();
          advertisement.title = this.title;
          advertisement.description = this.description;
          advertisement.pricePerView = this.pricePerView;
          return advertisement;
      }
  }

    public void incrementViews(int count) {
        this.totalViews += count;
    }
}
