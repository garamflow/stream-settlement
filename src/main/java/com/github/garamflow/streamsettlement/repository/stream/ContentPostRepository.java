package com.github.garamflow.streamsettlement.repository.stream;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentPostRepository extends JpaRepository<ContentPost, Long>, ContentPostCustomRepository {
}
