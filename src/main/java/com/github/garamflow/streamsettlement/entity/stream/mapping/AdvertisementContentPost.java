package com.github.garamflow.streamsettlement.entity.stream.mapping;

import com.github.garamflow.streamsettlement.entity.stream.advertisement.Advertisement;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "advertisement_content_post")
public class AdvertisementContentPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "advertisement_content_post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advertisement_id", nullable = false)
    private Advertisement advertisement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_post_id", nullable = false)
    private ContentPost contentPost;

    private AdvertisementContentPost(Advertisement advertisement, ContentPost contentPost) {
        this.advertisement = advertisement;
        this.contentPost = contentPost;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Getter
    public static class Builder {
        private Advertisement advertisement;
        private ContentPost contentPost;

        public Builder advertisement(Advertisement advertisement) {
            this.advertisement = advertisement;
            return this;
        }

        public Builder contentPost(ContentPost contentPost) {
            this.contentPost = contentPost;
            return this;
        }

        public AdvertisementContentPost build() {
            return new AdvertisementContentPost(advertisement, contentPost);
        }
    }
}
