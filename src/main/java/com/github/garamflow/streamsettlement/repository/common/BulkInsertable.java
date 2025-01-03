package com.github.garamflow.streamsettlement.repository.common;

import java.util.List;

/**
 * 엔티티의 벌크 삽입 기능을 제공하는 인터페이스입니다.
 * 대량의 데이터를 효율적으로 저장하기 위한 공통 인터페이스입니다.
 *
 * @param <T> 삽입할 엔티티 타입
 */
public interface BulkInsertable<T> {
    /**
     * 주어진 엔티티 목록을 데이터베이스에 벌크 삽입합니다.
     * 
     * @param items 삽입할 엔티티 목록
     */
    void bulkInsert(List<T> items);
}