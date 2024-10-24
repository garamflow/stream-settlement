# stream-settlement
## 개발 환경 설정 가이드
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
- .env.template 파일을 복사하여 .env 파일로 변경
- .env 파일을 열어 실제 값으로 수정
```env
# .env
# Google OAuth2 설정
GOOGLE_CLIENT_ID=your-actual-client-id
GOOGLE_CLIENT_SECRET=your-actual-client-secret

# 다른 설정들은 기본값 사용 가능
```

3. Docker 컨테이너 실행 (MySQL, Redis)
```bash
docker-compose -f docker-compose-local.yml up -d
```

4. 데이터베이스 접속 정보
- MySQL
```text
Host: localhost
Port: 3306
Database: mydb
Username: root
Password: password
```
- Redis
```text
Host: localhost
Port: 6379
Password: redispassword
```

5. 애플리케이션 실행
- IntelliJ 등을 통해 프로젝트 오픈
- 기본적으로 local 프로파일 실행

> 환경변수 설정 방법
> - 해당 프로젝트에서는 spring-dotenv 를 이용하고 있습니다.
> - `build.gradle`에 의존성 추가
>   - implementation 'me.paulschwarz:spring-dotenv:4.0.0'
> - .env 파일에 환경 변수 설정
> - applicayion.yml 등에서 `${PASSWORD}` 와 같이 불러올 수 있습니다.

## User 서비스
- 

## Streaming 서비스
- 

## 정산 서비스
- 