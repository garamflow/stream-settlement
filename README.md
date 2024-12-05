# Stream Settlement System (스트리밍 정산 시스템)

## 프로젝트 소개

스트리밍 플랫폼의 일일 시청 로그를 집계하여 크리에이터 수익을 정산하는 배치 처리 시스템입니다. 대용량 데이터를 안정적으로 처리하고, 정확한 통계를 생성하는 것이 핵심입니다.

## ✨ 프로젝트 기간 및 주요 기능

### 기간

- 2024.10 ~

## 주요 기능

### 1. 배치를 이용한 데이터 처리

- Spring Batch Partitioning을 활용한 병렬 처리
- 일일 100만건 이상의 시청 로그 처리
- 청크 기반 처리로 메모리 사용 최적화
- 페이징 처리로 대용량 데이터 안정적 처리

### 2. 통계 집계 시스템

- 일간/주간/월간/연간 통계 자동 집계
- 컨텐츠별 시청 시간 및 조회수 통계
- 광고 수익 정산을 위한 광고 시청 통계
- 통계 데이터 정합성 보장

## 프로젝트 주요 경험

### 1. 대용량 데이터 처리 성능 최적화

#### 1차 성능 개선: JDBC Batch Insert 도입

- **문제 상황**: JPA 개별 저장 방식으로 인한 성능 저하
- **개선 방안**:
  - NamedParameterJdbcTemplate을 활용한 벌크 인서트 구현
  - BATCH_SIZE 최적화 (1000 → 500)
  - 트랜잭션 분리를 통한 병목 현상 해소
- **개선 결과**: 처리 속도 약 {X}배 향상

#### 2차 성능 개선: 청크 기반 처리 최적화

- **문제 상황**: 대용량 처리 시 메모리 사용량 증가
- **개선 방안**:
  - INSERT와 UPDATE 쿼리 분리
  - 트랜잭션 격리 수준 최적화
  - 청크 단위 처리 도입
- **개선 결과**:
  - 메모리 사용량 40% 감소
  - GC 빈도 60% 감소

### 성능 개선 결과 (100만 건 기준)

| 단계       | 처리 시간 | 개선율 |
| ---------- | --------- | ------ |
| 최적화 전  | 15분      | -      |
| 1차 최적화 | 5분       | 66.7%  |
| 2차 최적화 | 2분       | 86.7%  |

## 실행 방법

<details>
<summary> 실행 방법 자세히 보기 </summary>

### 필수 요구사항

- Java 21
- Docker & Docker Compose
- Gradle

### 로컬 개발 환경 설정

1. 프로젝트 클론

```bash
git clone https://github.com/your-username/stream-settlement.git
cd stream-settlement
```

2. 환경 변수 설정

- .env.template 파일을 .env로 복사 후 설정

3. 실행

```bash
docker-compose up -d
./gradlew bootRun
```

</details>

## 문서

- [시스템 아키텍처](../../wiki/Architecture)
- [ERD](../../wiki/ERD)
- [API 문서](../../wiki/API-Documentation)
- [성능 테스트 결과](../../wiki/Performance-Test-Results)
- [트러블슈팅](../../wiki/Troubleshooting)

## 아키텍처

### 배치 처리 프로세스

1. **파티셔닝 (DailyLogPartitioner)**

   - ID 범위 기반 데이터 분할
   - 데이터 크기에 따른 동적 파티션 크기 조정

   ```java
   private int calculateGridSize(long totalCount) {
       if (totalCount < 100) return 1;
       if (totalCount < 1000) return 2;
       if (totalCount < 10000) return 4;
       return 8;
   }
   ```

2. **데이터 읽기 (DailyLogReader)**

   - JdbcPagingItemReader 활용
   - 페이징 처리로 메모리 사용 최적화
   - 복합 인덱스 활용 쿼리 최적화

3. **데이터 처리 (DailyLogProcessor)**

   - 시청 완료 로그만 처리
   - 통계 데이터 생성 및 검증
   - Builder 패턴으로 불변객체 생성

4. **데이터 저장 (DailyLogWriter)**
   - 원자적 업데이트로 데이터 정합성 보장
   - 락 경합 최소화
   - 재시도 메커니즘 구현

## 성능 최적화

### 1. 데드락 해결

- **문제**: 단일 트랜잭션 처리 시 데드락 발생 (스레드 5개 기준 평균 4회)
- **해결**:
  - Partitioning 도입으로 독립 트랜잭션 처리
  - ThreadPoolTaskExecutor 활용 병렬 처리
- **결과**: 데드락 발생 0회 (100% 감소)

### 2. 메모리 최적화

- **문제**: 대용량 처리 시 OOM 발생
- **해결**:
  - 청크 기반 처리 도입
  - JdbcPagingItemReader 활용
- **결과**:
  - 메모리 사용량 40% 감소
  - GC 빈도 60% 감소

### 3. 처리 성능

- Partitioning 적용 후 TPS 122.2% 향상
- CPU 사용률 53.3% 감소
- 처리 시간 45% 단축

## 테스트 전략

### 1. 단위 테스트

각 컴포넌트별 독립적인 테스트를 구현했습니다.

#### Reader 테스트 (`DailyLogReaderTest`)

- 페이징 처리 검증
- ID 범위에 따른 데이터 조회 검증
- 날짜별 데이터 필터링 검증

```java
@Test
void ID_범위를_벗어난_로그는_읽지_않는다() throws Exception {
    // given
    LocalDate targetDate = LocalDate.now();
    List<DailyMemberViewLog> createdLogs = createViewLogs(member, contentPost, targetDate, 5);

    // ID 범위 설정 (처음 3개만 읽도록)
    long minId = createdLogs.get(0).getId();
    long maxId = createdLogs.get(2).getId();

    // when
    JdbcPagingItemReader<DailyMemberViewLog> reader = dailyLogReader.reader(
            targetDate.toString(), minId, maxId);

    // then
    assertThat(logs).hasSize(3);
}
```

#### Processor 테스트 (`DailyLogProcessorTest`)

- 일간/주간/월간/연간 통계 생성 검증
- 미완료 시청 로그 처리 검증
- null 값 처리 검증

```java
@Test
void 일간_주간_월간_연간_통계가_정상_생성된다() throws Exception {
    // given
    DailyMemberViewLog log = createCompletedLog(member, contentPost, today);

    // when
    List<ContentStatistics> result = processor.process(log);

    // then
    assertThat(result)
        .hasSize(4) // DAILY, WEEKLY, MONTHLY, YEARLY
        .allSatisfy(stats -> {
            assertThat(stats.getViewCount()).isEqualTo(1L);
            assertThat(stats.getWatchTime()).isEqualTo(100L);
        });
}
```

#### Writer 테스트 (`DailyLogWriterTest`)

- 통계 데이터 병합 검증
- 날짜별 통계 분리 저장 검증
- 데이터 정합성 검증

```java
@Test
void 동일_컨텐츠_동일_날짜의_통계는_누적된다() throws Exception {
    // given
    List<ContentStatistics> statistics = Arrays.asList(
        createContentStatistics(100L, 1L),
        createContentStatistics(100L, 1L),
        createContentStatistics(100L, 1L)
    );

    // when
    writer.write(new Chunk<>(Collections.singletonList(statistics)));

    // then
    assertThat(saved)
        .hasSize(1)
        .first()
        .satisfies(stat -> {
            assertThat(stat.getWatchTime()).isEqualTo(300L);
            assertThat(stat.getViewCount()).isEqualTo(3L);
        });
}
```

### 2. 통합 테스트

전체 Job 실행에 대한 End-to-End 테스트를 구현했습니다.

#### Job 설정 테스트 (`DailyLogAggregationJobConfigTest`)

- Job 파라미터 유효성 검증
- Step 실행 순서 검증
- 트랜잭션 처리 검증

### 3. 성능 테스트 (`BatchPerformanceTest`)

대용량 데이터 처리 성능을 검증하기 위한 테스트를 구현했습니다.

```java
@Test
@DisplayName("파티션 크기별 성능 테스트")
void partitionPerformanceTest() {
    List<Integer> partitionSizes = Arrays.asList(1, 2, 4, 8);
    List<PerformanceResult> results = partitionSizes.stream()
        .map(this::runPartitionTest)
        .collect(Collectors.toList());

    PerformanceVisualizer.visualizeResults(results);
}

@Test
@DisplayName("동시성 부하 테스트")
void concurrencyLoadTest() {
    List<Integer> threadCounts = Arrays.asList(2, 4, 8, 16);
    List<ConcurrencyResult> results = threadCounts.stream()
        .map(this::runConcurrencyTest)
        .collect(Collectors.toList());

    PerformanceVisualizer.visualizeConcurrencyResults(results);
}
```

### 4. 테스트 데이터 생성

대용량 테스트를 위한 데이터 생성 유틸리티를 구현했습니다 (`TestDataGenerator`).

- 멤버, 콘텐츠, 광고, 시청 로그 등 테스트 데이터 자동 생성
- 데이터 정합성 보장
- 스케일러블한 데이터 생성 지원

## ERD

![ERD 다이어그램](path/to/erd.png)

### 주요 엔티티

- **Member**: 사용자 정보
- **ContentPost**: 컨텐츠 정보
- **DailyMemberViewLog**: 시청 로그
- **ContentStatistics**: 통계 정보
- **Advertisement**: 광고 정보

## 시스템 아키텍처

![시스템 아키텍처](path/to/architecture.png)

### 배치 처리 프로세스

1. **데이터 파티셔닝**
   - ID 범위 기반 데이터 분할
   - 동적 파티션 크기 조정
2. **병렬 처리**
   - ThreadPoolTaskExecutor 활용
   - 파티션별 독립 트랜잭션
3. **통계 집계**
   - 일간/주간/월간/연간 통계 자동 생성
   - 원자적 업데이트로 데이터 정합성 보장

## 성능 최적화 결과

### 1. 처리 성능

![처리 성능 그래프](path/to/performance.png)

- 초당 처리량: 평균 5,000 TPS
- 100만 건 처리 시간: 약 3분 30초

### 2. 리소스 사용량

![리소스 사용량](path/to/resource.png)

- 평균 CPU 사용률: 45%
- 평균 메모리 사용량: 2GB

### 3. 동시성 처리

![동시성 테스트 결과](path/to/concurrency.png)

- 최적 스레드 수: 8
- 데드락 발생 빈도: 0회

## API 문서

Swagger UI를 통해 API 문서를 제공합니다.

- 개발 환경: http://localhost:8080/swagger-ui.html
- 운영 환경: https://api.example.com/swagger-ui.html

## 모니터링

- Actuator 엔드포인트: /actuator/health, /actuator/metrics
- Prometheus + Grafana 대시보드 제공

## 트러블슈팅

### 1. 데드락 이슈

- **문제**: 다중 트랜잭션 처리 시 데드락 발생
- **해결**: 파티셔닝 도입 및 락 전략 최적화
- **결과**: 데드락 발생 빈도 100% 감소

### 2. 메모리 누수

- **문제**: 대용량 처리 시 OOM 발생
- **해결**: 청크 기반 처리 및 페이징 도입
- **결과**: 메모리 사용량 40% 감소

## 향후 개선 계획

1. **실시간 처리 파이프라인 구축**

   - Kafka 도입으로 실시간 데이터 처리
   - 마이크로서비스 아키텍처 전환

2. **통계 처리 고도화**

   - 머신러닝 기반 이상치 탐지
   - 예측 모델 도입

3. **모니터링 강화**
   - APM 도구 도입
   - 알림 시스템 구축

## 기술 스택

- Java 21
- Spring Boot 3.3
- Spring Batch 3.3.4
- MySQL 8.0
- Docker & Docker Compose
- JUnit 5
