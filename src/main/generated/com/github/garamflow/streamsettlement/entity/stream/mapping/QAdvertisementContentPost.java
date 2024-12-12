package com.github.garamflow.streamsettlement.entity.stream.mapping;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QAdvertisementContentPost is a Querydsl query type for AdvertisementContentPost
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAdvertisementContentPost extends EntityPathBase<AdvertisementContentPost> {

    private static final long serialVersionUID = 1214498562L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QAdvertisementContentPost advertisementContentPost = new QAdvertisementContentPost("advertisementContentPost");

    public final com.github.garamflow.streamsettlement.entity.stream.advertisement.QAdvertisement advertisement;

    public final com.github.garamflow.streamsettlement.entity.stream.content.QContentPost contentPost;

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public QAdvertisementContentPost(String variable) {
        this(AdvertisementContentPost.class, forVariable(variable), INITS);
    }

    public QAdvertisementContentPost(Path<? extends AdvertisementContentPost> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QAdvertisementContentPost(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QAdvertisementContentPost(PathMetadata metadata, PathInits inits) {
        this(AdvertisementContentPost.class, metadata, inits);
    }

    public QAdvertisementContentPost(Class<? extends AdvertisementContentPost> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.advertisement = inits.isInitialized("advertisement") ? new com.github.garamflow.streamsettlement.entity.stream.advertisement.QAdvertisement(forProperty("advertisement")) : null;
        this.contentPost = inits.isInitialized("contentPost") ? new com.github.garamflow.streamsettlement.entity.stream.content.QContentPost(forProperty("contentPost"), inits.get("contentPost")) : null;
    }

}

