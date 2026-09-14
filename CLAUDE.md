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

`application.yml`은 **구조만** git에 커밋되어 있고 (`${DB_PASSWORD}`, `${JWT_SECRET_KEY}`, `${ANTHROPIC_API_KEY}` 같은 기본값 없는 플레이스홀더만 있음), 실제 비밀값은 `spring.config.import: optional:classpath:application-secrets.yml`로 불러오는 **`src/main/resources/application-secrets.yml`**(파일 자체가 `.gitignore`됨, git에 절대 안 올라감)에 둡니다.

새 컴퓨터에서는 이 파일이 없으므로 직접 만들어야 합니다:

```yaml
# src/main/resources/application-secrets.yml (커밋 금지, .gitignore에 등록되어 있음)
DB_PASSWORD: 여기에_로컬_MySQL_비밀번호
JWT_SECRET_KEY: 아무_base64_문자열
ANTHROPIC_API_KEY: sk-ant-...
YOUTUBE_API_KEY: AIza...
```

이 파일만 만들어두면 IDE/터미널에 별도 환경변수를 설정하지 않아도 로컬에서 바로 실행됩니다. (env var로 덮어쓰고 싶으면 OS 환경변수로 `DB_PASSWORD`/`JWT_SECRET_KEY`/`ANTHROPIC_API_KEY`/`YOUTUBE_API_KEY`를 설정해도 동일하게 동작 — Spring이 어차피 이름이 같은 프로퍼티로 플레이스홀더를 채움).

`YOUTUBE_API_KEY`는 Google Cloud Console에서 **YouTube Data API v3**를 활성화하고 발급받은 API 키입니다(무료지만 일일 할당량 있음). 유튜브 레시피 검색/가져오기 기능에 쓰입니다.

`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`은 로컬 기본값(`localhost`/`3306`/`cookgenie`/`root`)이 있어서 별도 설정 없이 그대로 씁니다. 로컬 MySQL은 `sql/create_database.sql`로 `cookgenie` DB만 만들면 테이블은 앱 기동 시 자동 생성됩니다.

**배포 서버 쪽**은 `application-secrets.yml`이 이미지에 아예 없으므로(로컬 전용, git에도 안 올라가고 Docker 이미지에도 안 들어감) 관여하지 않고, `.github/workflows/deploy-to-ubuntu.yml`이 GitHub `ubuntu` 환경의 Secrets(`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `ANTHROPIC_API_KEY`, `YOUTUBE_API_KEY`, `GHCR_PAT`)에서 값을 읽어 서버의 `.env` 파일로 주입 → 컨테이너 실행 시 OS 환경변수로 전달되어 `application.yml`의 플레이스홀더를 채웁니다. **`YOUTUBE_API_KEY`는 GitHub `ubuntu` 환경 Secrets에 아직 등록 안 되어 있을 수 있으니 배포 전에 확인 필요.**

### 지나간 사고: application.yml이 통째로 배포에서 빠져있었던 문제

한동안 `application.yml` 전체가 `.gitignore`에 걸려있어서 **CI가 빌드하는 jar에 이 파일 자체가 아예 없었습니다.** 그 결과 DB 설정도 못 읽어서 Spring Boot가 조용히 임시 메모리 DB(H2)로 대체해버렸고, 아무도 눈치채지 못한 채 배포가 계속 "성공"하고 있었습니다. `anthropic.api-key`처럼 기본값 없는 필수 프로퍼티가 추가되고 나서야 크래시로 드러남. → 지금은 **구조(파일)는 커밋하되 진짜 비밀값만 별도의 gitignore된 `application-secrets.yml`로 분리**하는 구조로 해결함 (로컬 편의 + 프로덕션 정상 빌드를 동시에 만족).

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
| Recipe | **1, 2단계 완료**: AI 레시피 생성(냉장고 재료 기반), 유튜브 레시피 검색/가져오기, 재료 기반 레시피 추천, 목록/상세/삭제 |
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

## Recipe 2단계 — 유튜브 레시피 연동

- `GET /fridges/{fridgeId}/recipes/youtube/search?keyword=&limit=` — YouTube Data API v3 `search.list`로 요리 영상을 검색. `keyword`를 생략하면 냉장고 재료 이름(최대 3개)으로 검색어를 자동 구성. **결과는 저장되지 않는 미리보기**(videoId/제목/설명/채널명/썸네일/영상 URL)이고, 실제 레시피로 저장하려면 가져오기 API를 호출해야 함.
- `POST /recipes/youtube/import` (`{"videoId": "..."}`) — `videos.list`로 영상 상세(제목/설명/채널명)를 조회한 뒤, 제목+설명을 Claude에게 넘겨서 `ClaudeRecipeClient.parseFromYoutube()`로 재료/조리법/1인분 영양정보를 추출(설명이 부실하면 제목+일반 지식으로 추정). `recipeType=YOUTUBE`, `sourceUrl`, `authorNickname`(=채널명)으로 저장. **같은 videoId를 다시 가져오면 재호출 없이 기존 레시피를 그대로 반환**(sourceUrl 기준 중복 방지, `Ingredient` 재사용 설계와 동일한 철학).
- 새 클라이언트: `domain/recipe/external/YoutubeSearchClient`(YouTube Data API 래퍼), `ClaudeRecipeClient.parseFromYoutube()`(추가된 메서드, 기존 `recipeTool()` 스키마 재사용).
- `RecipeRepository.findBySourceUrl()` 추가.

## FridgeItem 통계 API

- `GET /fridges/{fridgeId}/statistics` — 총 재료 수, 소비기한 임박(3일 이내)/지남 개수, 카테고리·보관위치별 분포, 주의가 필요한 재료(임박+지남, 만료일 가까운 순 최대 5개), 오래 방치된 재료(구매일 오래된 순 최대 5개), 최근 150일 등록 활동 히트맵(`FridgeItem.createdAt` 날짜별 개수, 등록 없는 날도 0으로 포함)을 한 번에 내려줌.
- 별도 리포지토리 쿼리 없이 `fridgeItemRepository.findByFridgeId()`로 가져온 뒤 메모리에서 집계(냉장고당 재료 수가 적어서 문제없음). `FridgeItemService.getStatistics()`에 구현, 신규 컨트롤러 `FridgeStatisticsController`.
- 히트맵은 삭제된 재료는 반영 못 함(별도 활동 로그 테이블이 없어서 현재 남아있는 `FridgeItem.createdAt` 기준으로만 집계) — 프론트에서 참고.

## 다음 할 일 후보 (우선순위 순 아님, 상황 보고 정하기)

- `FridgeItem`/`Recipe` API들의 냉장고 멤버 권한 검증 (지금은 냉장고 존재 여부만 확인)
- 냉장고 멤버 초대 API
- 프론트엔드의 "영양정보 동기화" 버튼 — 이제 없는 엔드포인트(`/ingredients/sync-raw-materials`)를 호출하고 있어서 프론트에서 제거 필요
- 기존에 영양정보 없이 등록된 재료들(예: 계란/목살/양파/소금 등)을 일괄로 재추정하는 백필(backfill) 기능 (요청은 있었으나 미구현)
- 소셜 로그인 / 이메일 인증 / 비밀번호 재설정 / 로그아웃 시 JWT 즉시 무효화
- 식단 기록(MealLog) API
