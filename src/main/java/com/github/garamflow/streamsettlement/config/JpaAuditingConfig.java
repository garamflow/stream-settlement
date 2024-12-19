package com.github.garamflow.streamsettlement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정
 * - 엔티티의 생성/수정 시간을 자동으로 관리
 * - @CreatedDate, @LastModifiedDate 등의 어노테이션 활성화
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
} 