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

## 버그: 유튜브 검색 쿼터(하루 100회) 소진 → 인메모리 캐싱 추가

YouTube Data API v3의 `search.list`는 "Search Queries per day" 쿼터가 별도로 있는데 기본값이 **하루 100회**밖에 안 됨(전체 쿼터 10,000 units/일 ÷ search.list 100 units). 추천 화면 같은 데서 "초간단 요리", "가성비 요리" 등 여러 카테고리를 한꺼번에 조회하면 순식간에 소진되어 `429 Too Many Requests`가 뜸.

`YoutubeSearchClient.search()`에 검색어+개수 조합 기준 인메모리 캐시(`ConcurrentHashMap`, TTL 12시간)를 추가해서 완화함. 캐시 히트면 API를 안 부르고, 캐시가 만료됐어도 API 호출이 실패하면(쿼터 초과 등) 만료된 캐시라도 있으면 그걸 대신 반환(완전 실패보다 오래된 데이터라도 보여주는 게 나음). `getVideoDetail()`(videos.list, 쿼터 훨씬 가벼움)은 캐싱 안 함 — 문제는 search.list만이라서.

**서버 재시작하면 캐시가 날아가는 인메모리 캐시**라서 완전한 해결책은 아님 — 진짜 쿼터가 부족하면 Google Cloud Console에서 증설 요청 필요(에러 메시지에 링크 포함). 참고로 `RecipeService.searchYoutubeRecipes()`가 keyword에 항상 `" 레시피"`를 붙이는데, 프론트에서 이미 "레시피"가 포함된 keyword를 보내면 "OO 레시피 레시피"처럼 중복되는 것도 확인됨(캐시 키가 미묘하게 갈리는 부작용은 있지만 기능엔 문제 없어서 이번엔 손대지 않음).

## FridgeItem 통계 API

- `GET /fridges/{fridgeId}/statistics` — 총 재료 수, 소비기한 임박(3일 이내)/지남 개수, 카테고리·보관위치별 분포, 주의가 필요한 재료(임박+지남, 만료일 가까운 순 최대 5개), 오래 방치된 재료(구매일 오래된 순 최대 5개), 최근 150일 등록 활동 히트맵(`FridgeItem.createdAt` 날짜별 개수, 등록 없는 날도 0으로 포함)을 한 번에 내려줌.
- 별도 리포지토리 쿼리 없이 `fridgeItemRepository.findByFridgeId()`로 가져온 뒤 메모리에서 집계(냉장고당 재료 수가 적어서 문제없음). `FridgeItemService.getStatistics()`에 구현, 신규 컨트롤러 `FridgeStatisticsController`.
- 히트맵은 삭제된 재료는 반영 못 함(별도 활동 로그 테이블이 없어서 현재 남아있는 `FridgeItem.createdAt` 기준으로만 집계) — 프론트에서 참고.

## 버그: 가공식품/브랜드 상품명은 영양정보 추정이 안 됨 (수정함)

`ClaudeNutritionClient`의 프롬프트가 "식재료"(순수 원재료) 기준으로만 짜여 있어서, "하림 통살 유린기"처럼 브랜드명+상품명이 붙은 가공식품/냉동식품 이름을 넣으면 Claude가 "실제 식재료가 아니다"로 판단해 `isValidFood=false`를 반환하고, 그 결과 영양정보 없이(`dataSource=USER_INPUT`) 등록되는 문제가 있었음.

1. **1차 수정**: 프롬프트를 "브랜드명+상품명이 붙은 가공식품이어도 같은 종류 음식의 일반적인 영양성분으로 추정해줘"로 완화. 로컬 테스트 결과 "하림 통살 유린기"는 해결됐지만, "하림 안심 꿔바로우"(탕수육 계열 튀김요리)는 여전히 `isValidFood=false`로 거부됨 — 완화가 이름에 따라 일관되게 먹히지 않음.
2. **2차 수정**: 프롬프트를 "먼저 음식 종류를 유추해보고, 조금이라도 짐작 가능하면 반드시 true로 하고 최선의 추정치를 내놔라"로 더 강하게 못박고, 브랜드 상품명 예시를 추가(꿔바로우 사례 포함). 또한 `nutritionTool()`의 JSON 스키마 속성 순서를 `Map.of`(순서 미보장) 대신 `LinkedHashMap`으로 고정해서, 모델이 영양성분 숫자들을 먼저 채우고 `isValidFood` 판단을 맨 마지막에 하도록 순서를 바꿈(먼저 추정해보게 유도 → 성급한 거부 감소 기대).
3. **3차 수정 (현재) — 진짜 웹 검색 추가**: 등록은 되는데(`isValidFood=true`) 실제 값과 다르다는 피드백을 받음 — 애초에 이 클라이언트는 **웹 검색을 전혀 안 하고 Claude의 학습된 지식만으로 "추정"**하는 구조였음(그래서 특정 브랜드 제품의 정확한 포장지 영양정보와는 다를 수밖에 없었음). Anthropic Messages API의 서버사이드 `web_search` 도구(`web_search_20250305` — Haiku는 최신 동적 필터링 버전인 `web_search_20260209`을 지원 안 해서 기본형을 씀)를 `record_nutrition_estimate`와 함께 tools에 추가하고, `tool_choice`를 강제 호출(`{"type":"tool",...}`)에서 `{"type":"auto"}`로 바꿔서 Claude가 브랜드+상품명이 있는 이름은 먼저 검색해보고, 검색으로 못 찾으면 기존처럼 추정하도록 함. `max_tokens`도 검색 결과가 응답에 섞여 들어갈 걸 감안해 300→1500으로 늘림. **아직 실제 API로 검증 못 함.** 검색이 추가되면 호출당 지연시간/비용이 늘어난다는 점 참고(Anthropic 웹 검색은 사용 건당 별도 과금).

## 다음 할 일 후보 (우선순위 순 아님, 상황 보고 정하기)

- `FridgeItem`/`Recipe` API들의 냉장고 멤버 권한 검증 (지금은 냉장고 존재 여부만 확인)
- 냉장고 멤버 초대 API
- 프론트엔드의 "영양정보 동기화" 버튼 — 이제 없는 엔드포인트(`/ingredients/sync-raw-materials`)를 호출하고 있어서 프론트에서 제거 필요
- 기존에 영양정보 없이 등록된 재료들(예: 계란/목살/양파/소금 등)을 일괄로 재추정하는 백필(backfill) 기능 (요청은 있었으나 미구현)
- 소셜 로그인 / 이메일 인증 / 비밀번호 재설정 / 로그아웃 시 JWT 즉시 무효화
- 식단 기록(MealLog) API
