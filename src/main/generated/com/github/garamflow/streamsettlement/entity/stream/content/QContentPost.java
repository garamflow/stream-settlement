package com.github.garamflow.streamsettlement.entity.stream.content;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathInits;


/**
 * QContentPost is a Querydsl query type for ContentPost
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QContentPost extends EntityPathBase<ContentPost> {

    private static final long serialVersionUID = 671172502L;

    private static final PathInits INITS = PathInits.DIRECT2;

    public static final QContentPost contentPost = new QContentPost("contentPost");

    public final StringPath category = createString("category");

    public final ListPath<com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost, com.github.garamflow.streamsettlement.entity.stream.mapping.QAdvertisementContentPost> contentPosts = this.<com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost, com.github.garamflow.streamsettlement.entity.stream.mapping.QAdvertisementContentPost>createList("contentPosts", com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost.class, com.github.garamflow.streamsettlement.entity.stream.mapping.QAdvertisementContentPost.class, PathInits.DIRECT2);

    public final DateTimePath<java.time.LocalDateTime> createdAt = createDateTime("createdAt", java.time.LocalDateTime.class);

    public final StringPath description = createString("description");

    public final NumberPath<Integer> duration = createNumber("duration", Integer.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final com.github.garamflow.streamsettlement.entity.member.QMember member;

    public final EnumPath<ContentStatus> status = createEnum("status", ContentStatus.class);

    public final StringPath tags = createString("tags");

    public final StringPath thumbnailUrl = createString("thumbnailUrl");

    public final StringPath title = createString("title");

    public final NumberPath<Long> totalViews = createNumber("totalViews", Long.class);

    public final NumberPath<Long> totalWatchTime = createNumber("totalWatchTime", Long.class);

    public final DateTimePath<java.time.LocalDateTime> updatedAt = createDateTime("updatedAt", java.time.LocalDateTime.class);

    public final StringPath url = createString("url");

    public QContentPost(String variable) {
        this(ContentPost.class, forVariable(variable), INITS);
    }

    public QContentPost(Path<? extends ContentPost> path) {
        this(path.getType(), path.getMetadata(), PathInits.getFor(path.getMetadata(), INITS));
    }

    public QContentPost(PathMetadata metadata) {
        this(metadata, PathInits.getFor(metadata, INITS));
    }

    public QContentPost(PathMetadata metadata, PathInits inits) {
        this(ContentPost.class, metadata, inits);
    }

    public QContentPost(Class<? extends ContentPost> type, PathMetadata metadata, PathInits inits) {
        super(type, metadata, inits);
        this.member = inits.isInitialized("member") ? new com.github.garamflow.streamsettlement.entity.member.QMember(forProperty("member")) : null;
    }

}

