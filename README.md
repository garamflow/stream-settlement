# Stream Settlement System (스트리밍 정산 시스템)

## 프로젝트 소개

스트리밍 플랫폼의 일일 시청 로그를 집계하여 크리에이터 수익을 정산하는 배치 처리 시스템입니다. 대용량 데이터를 안정적으로 처리하고, 정확한 통계를 생성하는 것이 핵심입니다.

---

## ✨ 프로젝트 기간 및 주요 기능

### 기간

- 2024.10 ~

### 주요 기능

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

---

## 프로젝트 주요 경험
### 스트리밍 통계 및 정산 배치 최적화 성능 개선
- **[자세히 알아보기](https://github.com/garamflow/stream-settlement/wiki/%EC%8A%A4%ED%8A%B8%EB%A6%AC%EB%B0%8D-%ED%86%B5%EA%B3%84-%EB%B0%8F-%EC%A0%95%EC%82%B0-%EB%B0%B0%EC%B9%98-%EC%B5%9C%EC%A0%81%ED%99%94-%ED%9E%88%EC%8A%A4%ED%86%A0%EB%A6%AC)**

- **1차 최적화**: Spring Batch Partitioning으로 병렬 처리 도입
  - 데이터 크기에 따른 동적 Grid Size 설정으로 분산 처리 최적화
  - 데드락 발생 빈도 감소: 평균 4회 → 0회
  - 처리 성능 개선: TPS 기준 122.2% 향상
  - CPU 사용률 감소: 53.3% 감소

  <br>

- **2차 최적화**: JDBC Bulk Insert 도입으로 배치 성능 개선
  - JPA 개별 INSERT 방식에서 JDBC Bulk Insert 방식으로 전환
  - 1천만 건 기준 처리 시간 50% 단축 (4,123ms → 2,072ms)
  - 데이터 규모가 커질수록 성능 향상 폭이 증가 (10,000건: 36% → 10,000,000건: 50%)


- **3차 최적화**: Redis 캐시 기반 데이터 필터링 도입
  - 조회수 0인 컨텐츠 필터링으로 배치 처리 시간 67.4% 단축
  - Redis 활용으로 불필요한 데이터 처리 제거
  - 실제 처리가 필요한 데이터만으로 파티션 크기 최적화

---

## 🛠️ 기술 스택
| 분야            | 기술 스택                                                                                                      |
  |-----------------|----------------------------------------------------------------------------------------------------------------|
| **Language**    | ![Java](https://img.shields.io/badge/Java-21-%23ED8B00?logo=openjdk&logoColor=white) |
| **Framework**   | ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-%236DB33F?logo=spring-boot&logoColor=white) ![Spring Batch](https://img.shields.io/badge/Spring%20Batch-3.3.4-%236DB33F?logo=spring&logoColor=white) ![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-%236DB33F?logo=spring&logoColor=white)       |
| **Build**       | ![Gradle](https://img.shields.io/badge/Gradle-%2302303A?logo=gradle&logoColor=white)                           |
| **Database**    | ![MySQL](https://img.shields.io/badge/MySQL-8.0-%234479A1?logo=mysql&logoColor=white)                          |
| **DevOps**      | ![Docker](https://img.shields.io/badge/Docker-%232496ED?logo=docker&logoColor=white) ![Docker Compose](https://img.shields.io/badge/Docker%20Compose-%232496ED?logo=docker&logoColor=white)                           |
| **Testing**     | ![JUnit](https://img.shields.io/badge/JUnit-5-%2325A162?logo=junit5&logoColor=white)                           |
| **IDE**         | ![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ%20IDEA-%23000000?logo=intellij-idea&logoColor=white)    |
| **Version Control** | ![Git](https://img.shields.io/badge/Git-%23F05032?logo=git&logoColor=white)                               |

---

## Wiki

- **[실행 방법](https://github.com/garamflow/stream-settlement/wiki#%EC%8B%A4%ED%96%89-%EB%B0%A9%EB%B2%95)**
- **[시스템 아키텍처](../../wiki/Architecture)**
- **[ERD](../../wiki/ERD)**
- **[트러블슈팅](https://github.com/garamflow/stream-settlement/wiki#%ED%8A%B8%EB%9F%AC%EB%B8%94%EC%8A%88%ED%8C%85)**
- **[테스트 전략](https://github.com/garamflow/stream-settlement/wiki#%ED%85%8C%EC%8A%A4%ED%8A%B8-%EC%A0%84%EB%9E%B5)**
