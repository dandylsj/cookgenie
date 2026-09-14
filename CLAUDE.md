# CookGenie 프로젝트 안내 (Claude Code용)

이 파일은 다른 컴퓨터에서 이 프로젝트를 열어도 지금까지의 맥락을 빠르게 파악할 수 있도록 정리한 것입니다.
새 기기에서 작업을 이어갈 때는 이 파일 + `docs/API.md`(전체 API 명세)를 먼저 읽으세요.

## 프로젝트 개요

냉장고 재료 관리 + 영양정보 + AI 레시피 추천 백엔드. Spring Boot(Java) + MySQL.

- Backend: `Spring Boot 4.1.0`, Java 17, Gradle
- DB: MySQL 8, 로컬은 `ddl-auto: update`로 스키마 자동 생성 (Flyway는 만들어뒀지만 현재 `enabled: false`)
- 배포: Ubuntu 서버에 Docker 블루/그린 방식, GitHub Actions self-hosted runner
- 도메인: `https://cookgenie.dandyhomelab.uk` (Cloudflare Tunnel, 호스트 포트 8082 — 같은 서버의 hatoo=8080, lineofduty=8081과 겹치지 않게 분리)

## ⚠️ 환경설정 필수 — 새 컴퓨터에서 처음 열 때

`src/main/resources/application.yml`은 **DB 비밀번호/JWT 시크릿/Anthropic API 키가 들어있어서 `.gitignore`로 제외**되어 있습니다.
새 컴퓨터에서는 이 파일이 아예 없으므로 직접 만들어야 합니다. 필요한 값:

```yaml
spring:
  application:
    name: cookgenie
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:cookgenie}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:여기에_로컬_MySQL_비밀번호}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
  flyway:
    enabled: false
    locations: classpath:db/migration
    baseline-on-migrate: true

jwt:
  secret-key: ${JWT_SECRET_KEY:아무_base64_문자열}

springdoc:
  api-docs:
    version: openapi_3_0
  swagger-ui:
    path: /swagger-ui.html

anthropic:
  api-key: ${ANTHROPIC_API_KEY:sk-ant-...}
  model: claude-haiku-4-5-20251001
```

로컬 MySQL은 `sql/create_database.sql`로 `cookgenie` DB만 만들면 테이블은 앱 기동 시 자동 생성됩니다.

## 기술적으로 겪었던 특이사항 (재발 방지용)

- **Spring Boot 4.1.0은 Jackson 3(`tools.jackson.*`)을 씀** — `jackson-core`/`jackson-databind`는 `tools.jackson.core`/`tools.jackson.databind`로 이동했지만, `jackson-annotations`는 여전히 `com.fasterxml.jackson.annotation.*`. `ObjectMapper`를 직접 주입할 땐 반드시 `tools.jackson.databind.ObjectMapper`를 써야 함 (`com.fasterxml.jackson.databind.ObjectMapper`는 빈이 없어서 기동 실패).
- Windows Git Bash에서 curl로 한글을 직접 `-d`에 넣으면 인코딩이 깨짐 — 테스트할 땐 JSON을 파일로 먼저 만들고 `--data-binary @파일`로 보낼 것.
- gradlew 파일이 Windows에서 커밋되면 실행권한이 없어서 GitHub Actions(ubuntu-latest)에서 `Permission denied`로 실패함 → `git update-index --chmod=+x gradlew` 처리 완료.

## 도메인별 구현 현황

| 도메인 | 상태 |
|---|---|
| Auth | 완료 — 회원가입/로그인/로그아웃/탈퇴/토큰재발급/프로필. JWT, Spring Security |
| Fridge | 완료 — 생성/목록/단건조회/삭제 (OWNER만 삭제 가능) |
| FridgeItem | 완료 — CRUD, 재료 수량 기준 탄단지 자동 계산(단위 일치할 때만) |
| Ingredient | 완료 — 검색/등록/수정/삭제. **등록 시 Claude가 100g 기준 영양정보 자동 추정** (아래 참고) |
| Recipe | **1단계 완료**: AI 레시피 생성(냉장고 재료 기반), 재료 기반 레시피 추천, 목록/상세/삭제. **2단계 미착수**: 유튜브 레시피 연동 |
| MealLog / NutritionGoal | 엔티티만 있고 API 없음 |
| 소셜 로그인 / 이메일 인증 / 비밀번호 재설정 | 미구현 |

전체 엔드포인트 상세 스펙은 **`docs/API.md`** 참고.

## 재료 영양정보 — 설계가 두 번 바뀐 이력

1. **1차 시도**: 식약처(식품안전나라) `FoodNtrCpntDbInfo02` 공공데이터 API로 원재료 3704건을 배치 동기화. → 이름이 "양파_생것", "양파_자색양파_생것" 등으로 너무 세분화돼 있어서 검색 UX가 나빴음.
2. **최종 방식 (현재)**: 재료를 등록(`POST /ingredients`)할 때, 같은 이름이 이미 있으면 그대로 재사용하고, 완전히 새 이름이면 **Claude(Anthropic API)에게 tool-use로 100g 기준 평균 영양정보를 추정**시켜 저장(`dataSource=LLM_ESTIMATED`, `isVerified=false`). 같은 이름은 한 번만 추정되고 이후 재사용됨. 정부 데이터 배치 동기화 관련 코드는 전부 제거함.
3. Anthropic API 클라이언트는 `common/client/anthropic/`(요청/응답 공용 래퍼)에 있고, `domain/ingredient/external/ClaudeNutritionClient`(영양정보 추정)와 `domain/recipe/external/ClaudeRecipeClient`(레시피 생성)가 이걸 재사용함.

## Recipe 1단계에서 만든 것

- `POST /fridges/{fridgeId}/recipes/generate` — 냉장고 재료 목록을 Claude에게 전달해 레시피 생성(제목/조리법/재료/1인분 영양정보/태그). 재료는 `Ingredient` 마스터와 자동 매칭 시도(안 되면 텍스트로만 표시).
- `GET /fridges/{fridgeId}/recipes/recommendations` — 냉장고 재료와 겹치는 비율이 높은 순으로 기존 레시피 추천.
- `GET /recipes`, `GET /recipes/{id}`, `DELETE /recipes/{id}` — 기본 조회/삭제.
- `Recipe` 엔티티에 원래 없던 `instructions`(조리법 TEXT) 컬럼을 추가함.

## 다음 할 일 후보 (우선순위 순 아님, 상황 보고 정하기)

- **레시피 2단계**: 유튜브 레시피 연동 (YouTube Data API 키 필요, 영상 설명란에서 재료 추출은 Claude 파싱 필요할 가능성 높음)
- `FridgeItem`/`Recipe` API들의 냉장고 멤버 권한 검증 (지금은 냉장고 존재 여부만 확인)
- 냉장고 멤버 초대 API
- 프론트엔드의 "영양정보 동기화" 버튼 — 이제 없는 엔드포인트(`/ingredients/sync-raw-materials`)를 호출하고 있어서 프론트에서 제거 필요
- 기존에 영양정보 없이 등록된 재료들(예: 계란/목살/양파/소금 등)을 일괄로 재추정하는 백필(backfill) 기능 (요청은 있었으나 미구현)
- 소셜 로그인 / 이메일 인증 / 비밀번호 재설정 / 로그아웃 시 JWT 즉시 무효화
- 식단 기록(MealLog) API
