package com.github.garamflow.streamsettlement.entity.stream.advertisement;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QAdvertisement is a Querydsl query type for Advertisement
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAdvertisement extends EntityPathBase<Advertisement> {

    private static final long serialVersionUID = -1099180882L;

    public static final QAdvertisement advertisement = new QAdvertisement("advertisement");

    public final ListPath<com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost, com.github.garamflow.streamsettlement.entity.stream.mapping.QAdvertisementContentPost> contentPosts = this.<com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost, com.github.garamflow.streamsettlement.entity.stream.mapping.QAdvertisementContentPost>createList("contentPosts", com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost.class, com.github.garamflow.streamsettlement.entity.stream.mapping.QAdvertisementContentPost.class, PathInits.DIRECT2);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath description = createString("description");

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<Long> pricePerView = createNumber("pricePerView", Long.class);

    public final StringPath title = createString("title");

    public final NumberPath<Long> totalViews = createNumber("totalViews", Long.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public QAdvertisement(String variable) {
        super(Advertisement.class, forVariable(variable));
    }

    public QAdvertisement(Path<? extends Advertisement> path) {
        super(path.getType(), path.getMetadata());
    }

    public QAdvertisement(PathMetadata metadata) {
        super(Advertisement.class, metadata);
    }

}

