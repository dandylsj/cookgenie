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
COUPANG_ACCESS_KEY: 쿠팡파트너스에서 발급받은 액세스 키
COUPANG_SECRET_KEY: 쿠팡파트너스에서 발급받은 시크릿 키
```

이 파일만 만들어두면 IDE/터미널에 별도 환경변수를 설정하지 않아도 로컬에서 바로 실행됩니다. (env var로 덮어쓰고 싶으면 OS 환경변수로 `DB_PASSWORD`/`JWT_SECRET_KEY`/`ANTHROPIC_API_KEY`/`YOUTUBE_API_KEY`/`COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY`를 설정해도 동일하게 동작 — Spring이 어차피 이름이 같은 프로퍼티로 플레이스홀더를 채움).

`YOUTUBE_API_KEY`는 Google Cloud Console에서 **YouTube Data API v3**를 활성화하고 발급받은 API 키입니다(무료지만 일일 할당량 있음). 유튜브 레시피 검색/가져오기 기능에 쓰입니다.

`COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY`는 **쿠팡파트너스** 가입 후 발급받는 키입니다(HMAC 서명 인증). 값이 없거나 틀려도 앱은 정상 기동하고, `GET /coupang/search` 호출만 실패해서 빈 리스트를 반환합니다(크래시 안 남) — 로컬에서 이 기능을 안 쓸 거면 아무 문자열이나 넣어둬도 됨.

`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`은 로컬 기본값(`localhost`/`3306`/`cookgenie`/`root`)이 있어서 별도 설정 없이 그대로 씁니다. 로컬 MySQL은 `sql/create_database.sql`로 `cookgenie` DB만 만들면 테이블은 앱 기동 시 자동 생성됩니다.

**배포 서버 쪽**은 `application-secrets.yml`이 이미지에 아예 없으므로(로컬 전용, git에도 안 올라가고 Docker 이미지에도 안 들어감) 관여하지 않고, `.github/workflows/deploy-to-ubuntu.yml`이 GitHub `ubuntu` 환경의 Secrets(`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `ANTHROPIC_API_KEY`, `YOUTUBE_API_KEY`, `COUPANG_ACCESS_KEY`, `COUPANG_SECRET_KEY`, `GHCR_PAT`)에서 값을 읽어 서버의 `.env` 파일로 주입 → 컨테이너 실행 시 OS 환경변수로 전달되어 `application.yml`의 플레이스홀더를 채웁니다. **`COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY`는 GitHub `ubuntu` 환경 Secrets에 아직 등록 안 되어 있을 수 있으니 배포 전에 확인 필요** (없어도 크래시는 안 나고 쿠팡 검색만 안 됨).

### 지나간 사고: application.yml이 통째로 배포에서 빠져있었던 문제

한동안 `application.yml` 전체가 `.gitignore`에 걸려있어서 **CI가 빌드하는 jar에 이 파일 자체가 아예 없었습니다.** 그 결과 DB 설정도 못 읽어서 Spring Boot가 조용히 임시 메모리 DB(H2)로 대체해버렸고, 아무도 눈치채지 못한 채 배포가 계속 "성공"하고 있었습니다. `anthropic.api-key`처럼 기본값 없는 필수 프로퍼티가 추가되고 나서야 크래시로 드러남. → 지금은 **구조(파일)는 커밋하되 진짜 비밀값만 별도의 gitignore된 `application-secrets.yml`로 분리**하는 구조로 해결함 (로컬 편의 + 프로덕션 정상 빌드를 동시에 만족).

## 기술적으로 겪었던 특이사항 (재발 방지용)

- **Spring Boot 4.1.0은 Jackson 3(`tools.jackson.*`)을 씀** — `jackson-core`/`jackson-databind`는 `tools.jackson.core`/`tools.jackson.databind`로 이동했지만, `jackson-annotations`는 여전히 `com.fasterxml.jackson.annotation.*`. `ObjectMapper`를 직접 주입할 땐 반드시 `tools.jackson.databind.ObjectMapper`를 써야 함 (`com.fasterxml.jackson.databind.ObjectMapper`는 빈이 없어서 기동 실패).
- Windows Git Bash에서 curl로 한글을 직접 `-d`에 넣으면 인코딩이 깨짐 — 테스트할 땐 JSON을 파일로 먼저 만들고 `--data-binary @파일`로 보낼 것.
- Windows Git Bash의 curl(8.17, mingw 빌드)에서 `-F "image=@경로;type=image/png"`처럼 `;type=`을 붙이면 원인 불명으로 `exit 26 (Failed to read local file)`이 남 — 파일은 실제로 존재하고 다른 호스트(httpbin 등)로는 정상 업로드됨. `;type=...` 없이 `-F "image=@경로"`만 쓰면 정상 동작(Spring이 확장자로 content-type 잘 추론함).
- gradlew 파일이 Windows에서 커밋되면 실행권한이 없어서 GitHub Actions(ubuntu-latest)에서 `Permission denied`로 실패함 → `git update-index --chmod=+x gradlew` 처리 완료.

## 도메인별 구현 현황

| 도메인 | 상태 |
|---|---|
| Auth | 완료 — 회원가입/로그인/로그아웃/탈퇴/토큰재발급/프로필 + **게스트 시작/게스트→정식회원 전환**. JWT, Spring Security |
| Fridge | 완료 — 생성/목록/단건조회/삭제(OWNER만) + **4자리 초대코드 발급/참여로 공유** |
| FridgeItem | 완료 — CRUD, 재료 수량 기준 탄단지 자동 계산(단위 일치할 때만) |
| Ingredient | 완료 — 검색/등록/수정/삭제. **등록 시 Claude가 100g 기준 영양정보 자동 추정** (아래 참고) |
| Recipe | **1, 2단계 완료**: AI 레시피 생성(냉장고 재료 기반), 유튜브 레시피 검색/가져오기, 재료 기반 레시피 추천, 목록/상세/삭제. **각 재료의 냉장고 보유 여부(inFridge)도 계산** |
| Receipt(영수증 인식) | **1/3단계 완료**: 영수증 사진 → Claude 비전으로 식재료 후보 추출(미리보기만, 저장은 안 함). 구매내역 캡처/실물 사진 인식은 미착수 |
| Shopping(장보기) | 완료 — 냉장고별 장보기 리스트 추가/조회/체크/삭제, **쿠팡파트너스 연동 최저가 검색** |
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
- `AiRecipeGenerateRequest.useFridgeIngredients`(기본 `true`) — `false`로 보내면 냉장고 재료를 완전히 무시하고 `note`에만 맞는 레시피를 자유 생성함(`ClaudeRecipeClient.generate()`가 `availableIngredients` 빈 리스트일 때 자유 생성 분기를 탐). 지금 냉장고에 없는 재료로 레시피를 시도해보고 싶다는 요청으로 추가함. `true`일 때만 냉장고에 재료가 없으면 400 에러가 남.

## 버그: AI 레시피 조리법이 너무 대략적/모호함 (프롬프트 보강으로 해결)

다른 앱(만개의레시피류)과 비교하면 AI가 생성하는 `instructions`가 "적당히 볶는다"처럼 뭉뚱그려 나오는 경우가 많다는 피드백. Claude API 비용 부담 때문에 필드를 늘리는 대신(난이도/조리시간/조리도구 등 신규 컬럼 추가는 응답이 커지고 구조 변경도 필요), **기존 스키마는 그대로 두고 프롬프트만 강화**하는 쪽으로 해결함 — 추가 API 호출도, 스키마 변경도 없어서 비용 영향이 거의 없음.

`ClaudeRecipeClient`에 `DETAIL_INSTRUCTION` 상수를 추가해서 `generate()`/`parseFromYoutube()` 프롬프트 끝에 공통으로 붙임: "최소 5단계 이상으로 나누고, 각 단계마다 시간/불 세기/재료 손질법/익었는지 확인하는 방법을 포함해라, '적당히'/'알맞게' 같은 모호한 표현은 쓰지 마라"는 내용. `recipeTool()`의 `instructions` 필드 description에도 같은 요구사항 + 구체적인 예시 문장을 넣어서 스키마 레벨에서도 한 번 더 강제함. 더 긴 응답을 감안해 `max_tokens`도 1200→1600으로 상향.

## 버그: 위 프롬프트 보강 이후 일부 레시피의 조리 순서가 통째로 빈 채로 저장됨 (수정함)

조리법을 더 자세히 쓰라고 프롬프트를 강화한 직후, 재료가 많거나(예: 재료 10개) 조리 순서가 긴 레시피에서 `조리 순서` 섹션이 완전히 빈 채로 저장되는 사례가 발생. 원인은 `ClaudeNutritionClient`에서 이미 한 번 겪었던 것과 같은 종류의 버그: `recipeTool()`의 JSON 스키마 `properties`를 `Map.of()`로 만들어서 필드 순서가 보장되지 않았고, 스키마상 `instructions`가 (사실상) 맨 마지막에 채워질 수 있는 위치였음. 응답이 길어지면서 `max_tokens`(1600)에 걸려 응답이 중간에 끊기면, 이미 채워진 title/영양정보/ingredients/tags는 저장되지만 아직 안 채워진 `instructions`는 그냥 누락되어(`generated.instructions()==null`) `Recipe.instructions`가 null로 저장되고, 프론트에는 "조리 순서" 헤더만 있고 내용이 하나도 없는 빈 상태로 보임.

`recipeTool()`의 `properties`를 `Map.of()` → `LinkedHashMap`으로 바꾸고, `instructions`를 `ingredients`/`tags`보다 앞(영양정보 필드들 바로 다음)으로 옮겨서 응답이 잘리더라도 `instructions`가 먼저 채워지도록 순서를 고정함. `max_tokens`도 1600→2200으로 한 번 더 올림.

**→ 이 수정 배포 후에도 재발 (추가 수정함)**: 배포 후 실제로 확인해보니 새로 생성한 레시피마다 재료/영양정보는 정상인데 `조리 순서`만 매번 비어있었음. 재료(ingredients, instructions보다 스키마상 뒤에 위치)는 항상 정상적으로 채워졌다는 점에서 **응답이 잘리는 게 아니라, 모델이 빈 배열로 스키마 요건 자체는 만족시켜버리는 것**이 진짜 원인이었음 — `instructions`를 배열 타입으로만 선언하고 최소 개수 제약을 걸지 않아서, 모델이 빈 배열(`[]`)을 내놔도 required 검증은 통과해버림. 두 가지로 수정:
1. `instructions` 스키마에 `"minItems": 3`을 추가해서 빈 배열이 스키마 자체를 위반하도록 강제함.
2. `ClaudeRecipeClient`에 `callAndExtract()` 공통 헬퍼를 추가 — 응답의 `instructions`가 null/빈 배열이면 같은 요청으로 한 번 더 재시도하고, 재시도까지 실패하면 `Optional.empty()`를 반환해서 (예전처럼 조리 순서 없는 레시피를 그대로 저장하는 대신) `RecipeService`의 기존 실패 처리 경로(`RECIPE_GENERATION_FAILED` 예외)를 타게 함. `generate()`/`parseFromYoutube()` 둘 다 이 헬퍼를 씀.

## Recipe 2단계 — 유튜브 레시피 연동

- `GET /fridges/{fridgeId}/recipes/youtube/search?keyword=&limit=` — YouTube Data API v3 `search.list`로 요리 영상을 검색. `keyword`를 생략하면 냉장고 재료 이름(최대 3개)으로 검색어를 자동 구성. **결과는 저장되지 않는 미리보기**(videoId/제목/설명/채널명/썸네일/영상 URL)이고, 실제 레시피로 저장하려면 가져오기 API를 호출해야 함.
- `POST /recipes/youtube/import` (`{"videoId": "..."}`) — `videos.list`로 영상 상세(제목/설명/채널명)를 조회한 뒤, 제목+설명을 Claude에게 넘겨서 `ClaudeRecipeClient.parseFromYoutube()`로 재료/조리법/1인분 영양정보를 추출(설명이 부실하면 제목+일반 지식으로 추정). `recipeType=YOUTUBE`, `sourceUrl`, `authorNickname`(=채널명)으로 저장. **같은 videoId를 다시 가져오면 재호출 없이 기존 레시피를 그대로 반환**(sourceUrl 기준 중복 방지, `Ingredient` 재사용 설계와 동일한 철학).
- 새 클라이언트: `domain/recipe/external/YoutubeSearchClient`(YouTube Data API 래퍼), `ClaudeRecipeClient.parseFromYoutube()`(추가된 메서드, 기존 `recipeTool()` 스키마 재사용).
- `RecipeRepository.findBySourceUrl()` 추가.

## 버그: 유튜브 검색 쿼터(하루 100회) 소진 → 인메모리 캐싱 추가

YouTube Data API v3의 `search.list`는 "Search Queries per day" 쿼터가 별도로 있는데 기본값이 **하루 100회**밖에 안 됨(전체 쿼터 10,000 units/일 ÷ search.list 100 units). 추천 화면 같은 데서 "초간단 요리", "가성비 요리" 등 여러 카테고리를 한꺼번에 조회하면 순식간에 소진되어 `429 Too Many Requests`가 뜸.

`YoutubeSearchClient.search()`에 검색어+개수 조합 기준 인메모리 캐시(`ConcurrentHashMap`, TTL 12시간)를 추가해서 완화함. 캐시 히트면 API를 안 부르고, 캐시가 만료됐어도 API 호출이 실패하면(쿼터 초과 등) 만료된 캐시라도 있으면 그걸 대신 반환(완전 실패보다 오래된 데이터라도 보여주는 게 나음). `getVideoDetail()`(videos.list, 쿼터 훨씬 가벼움)은 캐싱 안 함 — 문제는 search.list만이라서.

**서버 재시작하면 캐시가 날아가는 인메모리 캐시**라서 완전한 해결책은 아님 — 진짜 쿼터가 부족하면 Google Cloud Console에서 증설 요청 필요(에러 메시지에 링크 포함). 참고로 `RecipeService.searchYoutubeRecipes()`가 keyword에 항상 `" 레시피"`를 붙이는데, 프론트에서 이미 "레시피"가 포함된 keyword를 보내면 "OO 레시피 레시피"처럼 중복되는 것도 확인됨(캐시 키가 미묘하게 갈리는 부작용은 있지만 기능엔 문제 없어서 이번엔 손대지 않음).

## 카테고리별 추천 재료 목록

- `GET /ingredients/categories/{categoryId}/suggestions` — 재료 추가 화면에서 카테고리를 고르면 보여줄 자주 쓰는 재료 이름 목록(정적 데이터, DB/AI 호출 없음). `domain/ingredient/IngredientSuggestions`에 카테고리 이름 → 추천 이름 목록을 하드코딩해둠(`CategorySeeder`의 12개 기본 카테고리와 맞춰야 함).
- **왜 정적 목록인가**: 서버 기동 시 150개 재료를 미리 Claude로 다 추정해서 시딩하는 방식도 고려했지만, 아무도 안 고르는 재료까지 토큰을 쓰게 되므로 채택 안 함. 대신 이름만 고정해서 보여주고, 실제 등록/영양정보 추정은 사용자가 고른 시점에 기존 `POST /ingredients`(같은 이름 재사용 or Claude 추정) 흐름을 그대로 타게 함 — 이러면 여러 사용자가 같은 재료를 여러 번 골라도 이름이 항상 똑같아서 두 번째부터는 재사용되고, 표기 차이(예: "돼지고기" vs "돼지 고기")로 인한 중복 등록/중복 추정도 원천 차단됨.
- `ErrorMessage.CATEGORY_NOT_FOUND` 추가.

## FridgeItem 통계 API

- `GET /fridges/{fridgeId}/statistics` — 총 재료 수, 소비기한 임박(3일 이내)/지남 개수, 카테고리·보관위치별 분포, 주의가 필요한 재료(임박+지남, 만료일 가까운 순 최대 5개), 오래 방치된 재료(구매일 오래된 순 최대 5개), 최근 150일 등록 활동 히트맵(`FridgeItem.createdAt` 날짜별 개수, 등록 없는 날도 0으로 포함)을 한 번에 내려줌.
- 별도 리포지토리 쿼리 없이 `fridgeItemRepository.findByFridgeId()`로 가져온 뒤 메모리에서 집계(냉장고당 재료 수가 적어서 문제없음). `FridgeItemService.getStatistics()`에 구현, 신규 컨트롤러 `FridgeStatisticsController`.
- 히트맵은 삭제된 재료는 반영 못 함(별도 활동 로그 테이블이 없어서 현재 남아있는 `FridgeItem.createdAt` 기준으로만 집계) — 프론트에서 참고.

## 버그: 가공식품/브랜드 상품명은 영양정보 추정이 안 됨 (수정함)

`ClaudeNutritionClient`의 프롬프트가 "식재료"(순수 원재료) 기준으로만 짜여 있어서, "하림 통살 유린기"처럼 브랜드명+상품명이 붙은 가공식품/냉동식품 이름을 넣으면 Claude가 "실제 식재료가 아니다"로 판단해 `isValidFood=false`를 반환하고, 그 결과 영양정보 없이(`dataSource=USER_INPUT`) 등록되는 문제가 있었음.

1. **1차 수정**: 프롬프트를 "브랜드명+상품명이 붙은 가공식품이어도 같은 종류 음식의 일반적인 영양성분으로 추정해줘"로 완화. 로컬 테스트 결과 "하림 통살 유린기"는 해결됐지만, "하림 안심 꿔바로우"(탕수육 계열 튀김요리)는 여전히 `isValidFood=false`로 거부됨 — 완화가 이름에 따라 일관되게 먹히지 않음.
2. **2차 수정**: 프롬프트를 "먼저 음식 종류를 유추해보고, 조금이라도 짐작 가능하면 반드시 true로 하고 최선의 추정치를 내놔라"로 더 강하게 못박고, 브랜드 상품명 예시를 추가(꿔바로우 사례 포함). 또한 `nutritionTool()`의 JSON 스키마 속성 순서를 `Map.of`(순서 미보장) 대신 `LinkedHashMap`으로 고정해서, 모델이 영양성분 숫자들을 먼저 채우고 `isValidFood` 판단을 맨 마지막에 하도록 순서를 바꿈(먼저 추정해보게 유도 → 성급한 거부 감소 기대).
3. **3차 수정 (현재) — 진짜 웹 검색 추가**: 등록은 되는데(`isValidFood=true`) 실제 값과 다르다는 피드백을 받음 — 애초에 이 클라이언트는 **웹 검색을 전혀 안 하고 Claude의 학습된 지식만으로 "추정"**하는 구조였음(그래서 특정 브랜드 제품의 정확한 포장지 영양정보와는 다를 수밖에 없었음). Anthropic Messages API의 서버사이드 `web_search` 도구(`web_search_20250305` — Haiku는 최신 동적 필터링 버전인 `web_search_20260209`을 지원 안 해서 기본형을 씀)를 `record_nutrition_estimate`와 함께 tools에 추가하고, `tool_choice`를 강제 호출(`{"type":"tool",...}`)에서 `{"type":"auto"}`로 바꿔서 Claude가 브랜드+상품명이 있는 이름은 먼저 검색해보고, 검색으로 못 찾으면 기존처럼 추정하도록 함. `max_tokens`도 검색 결과가 응답에 섞여 들어갈 걸 감안해 300→1500으로 늘림. **아직 실제 API로 검증 못 함.** 검색이 추가되면 호출당 지연시간/비용이 늘어난다는 점 참고(Anthropic 웹 검색은 사용 건당 별도 과금).

## 게스트 로그인 (회원가입 없이 바로 시작)

첫 사용자의 진입장벽을 낮추기 위해, 회원가입 없이 바로 냉장고/재료/레시피 기능을 다 써볼 수 있는 게스트 모드를 추가함.

- `POST /auth/guest` — 인증 불필요, body 없음. `User.provider="GUEST"`(원래 소셜로그인용으로 만들어뒀던 컬럼을 재활용), 자동 생성된 고유 이메일/아이디로 진짜 유저 row를 만들고 즉시 access/refresh 토큰 발급. 이후 냉장고 생성/재료 등록/AI 레시피 생성 등 **일반 회원과 완전히 동일하게** 동작함(로컬 스토리지에 데이터를 따로 들고 있는 방식이 아니라 서버에 진짜 계정을 만드는 방식 — 영양정보 추정/AI 레시피 생성이 어차피 서버 호출이 필요해서 이렇게 설계함).
- `POST /auth/guest/upgrade` — 인증 필요(게스트 토큰). body는 회원가입과 동일(`SignupRequest`). **같은 유저 id를 그대로 승격**시키는 방식이라 게스트로 쌓아둔 냉장고/재료 데이터가 이관 없이 그대로 유지됨. 성공 시 새 토큰을 발급하므로 프론트는 기존 게스트 토큰을 새 토큰으로 교체해야 함.
- `User.upgradeFromGuest()`가 loginId/password/email/nickname을 채우고 provider/providerId를 null로 지워서 이후 `signup()`의 소셜/게스트 판별 로직과 충돌하지 않게 함.
- **3일 미전환 시 자동 삭제**: `GuestCleanupScheduler`(`@Scheduled(cron="0 0 * * * *")`, 매시 정각)가 `provider="GUEST"`이고 `createdAt`이 `User.GUEST_RETENTION_DAYS`(=3일)보다 오래된 유저를 찾아서, 소유한 Fridge/FridgeItem/ShoppingItem/FridgeMember와 RefreshToken까지 함께 정리(FK 제약 때문에 `FridgeService.deleteFridge()`와 동일한 순서: FridgeItem→ShoppingItem→FridgeMember→Fridge→User)한 뒤 유저 자체를 삭제함. `CookgenieApplication`에 `@EnableScheduling` 추가함(원래 없었음, MFDS 배치 스케줄러 제거할 때 `SchedulingConfig`도 같이 지웠었음).
- **프론트 공지용 정보**: `GET /auth/profile`(`UserInfoResponse`)에 `guest`(boolean)와 `guestExpiresAt`(게스트일 때만 값 있음 = `createdAt + 3일`) 필드를 추가함. 프론트에서 게스트 로그인 직후 또는 프로필 조회 시 이 값으로 "n일 후 데이터가 삭제됩니다 — 지금 회원가입하고 이어가기" 같은 배너를 띄우면 됨.
- 로컬에서 게스트 생성 → 냉장고 생성 → 게스트→회원 전환 → 새 토큰으로 로그인까지 curl로 end-to-end 검증 완료.

## 냉장고 공유 (초대코드)

hatoo 프로젝트(`C:\hatto`, `domain/groups`)의 그룹 초대코드 방식을 그대로 참고해서 만듦.

- `POST /fridges/{fridgeId}/invite-code` (OWNER만) — 4자리 숫자 코드를 생성해 `Fridge.inviteCode`/`inviteCodeExpiryDate`(7일 후)에 저장. hatoo처럼 냉장고당 코드를 하나만 유지하고 재발급하면 이전 코드는 그냥 덮어써져서 무효화됨.
- `POST /fridges/join` (`{"inviteCode":"1234"}`) — hatoo는 `{groupId}/{token}` 경로로 그룹 id를 미리 알아야 참여 가능했지만, cookgenie는 **코드만 입력하면 되도록** 단순화함(`FridgeRepository.findByInviteCodeAndInviteCodeExpiryDateAfter()`로 코드만으로 활성 냉장고를 바로 찾음). 이미 멤버면 409, 코드가 없거나 만료됐으면 400.
- 코드 생성 시 `FridgeRepository.existsByInviteCodeAndInviteCodeExpiryDateAfter()`로 현재 유효한 다른 코드와 안 겹치는 값이 나올 때까지 재시도(`generateUniqueInviteCode()`).
- hatoo는 그룹 인원 최대 5명 제한 + Redis(Redisson) 분산 락으로 동시 참여 레이스를 막았지만, cookgenie는 Redis 인프라가 없고 인원 제한 요구사항도 없어서 **그대로 가져오지 않음** — `fridge_members(fridge_id, user_id)` unique 제약으로 중복 참여만 막음. 동시성이 실제로 문제가 되면 그때 Redis 도입을 검토.

## 장보기 리스트 + 쿠팡 최저가 검색 + AI 레시피 부족 재료 연동

`domain/shopping/` 신규 패키지. 세 가지가 맞물려 동작함:

1. **장보기 리스트 (`ShoppingItem`, 냉장고별)**: `POST/GET /fridges/{fridgeId}/shopping-items`, `PATCH .../{itemId}`(완료 체크), `DELETE .../{itemId}`. `Ingredient` 마스터와 무관하게 이름(`name`)만 저장하는 단순 리스트 — 여기는 영양정보 추정이 필요 없어서 `POST /ingredients`의 Claude 추정 흐름을 안 탐. `FridgeItemController`처럼 별도 멤버십 검증 없이 냉장고 존재 여부만 확인(기존 코드베이스의 느슨한 권한 검증 관례를 그대로 따름 — 아래 "다음 할 일 후보"의 멤버 권한 검증 항목이 해결되면 같이 처리하면 됨).
2. **쿠팡 최저가 검색 (`GET /coupang/search?keyword=&limit=`)**: **쿠팡파트너스 Open API**(`GET .../affiliate_open_api/apis/openapi/products/search`)를 HMAC-SHA256 서명(`CoupangProductClient.generateAuthorization()`, 알고리즘명 `CEA`)으로 직접 호출. `COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY` 필요(환경설정 섹션 참고). 키가 없거나 호출이 실패하면 예외를 던지지 않고 **빈 리스트**를 반환(다른 외부 API 클라이언트들과 동일한 방어적 설계 — `ClaudeNutritionClient`/`YoutubeSearchClient`도 실패 시 각각 empty/캐시로 대체).
   - **겪은 버그 (수정함)**: 실제 키로 처음 호출했을 때 `401 Invalid signature`가 남. 원인은 `RestClient.get().uri(String)`에 이미 퍼센트 인코딩된 쿼리 문자열을 그대로 넘겼더니, 내부적으로 `UriComponentsBuilder`가 이걸 다시 인코딩(이중 인코딩)해버려서 실제 전송된 쿼리와 서명에 쓴 쿼리가 달라진 것. `URI.create(...)`로 URI를 직접 만들어 `.uri(URI)`로 넘기는 방식으로 재인코딩을 우회해서 해결함. **실제 키로 라이브 검증 완료** — "양송이스프" 검색 시 실제 쿠팡 상품/가격/이미지/링크가 정상적으로 옴.
   - JSON 응답 필드명 주의: Java 필드는 `isRocket`/`isFreeShipping`이지만 boolean getter 관례상 JSON에는 `rocket`/`freeShipping`으로 내려감(`isGuest`→`guest`와 동일한 패턴).
3. **AI 레시피 ↔ 장보기 연동**: `RecipeIngredientResponse`에 `inFridge`(Boolean, nullable) 추가. `POST /fridges/{fridgeId}/recipes/generate`는 항상 그 fridgeId 기준으로 계산해서 내려주고, `GET /recipes/{id}?fridgeId=`도 쿼리파라미터로 주면 같은 걸 계산함(안 주면 전부 null). 매칭은 `Ingredient` 마스터 매칭 여부(`matched`)와 무관하게 **이름을 정규화(trim+소문자)해서 냉장고 재료 이름 집합과 비교**하는 방식(`RecipeService.normalizeNames()`) — `RecipeIngredient.ingredient`가 null이어도(매칭 안 된 텍스트 재료) 이름만 같으면 in Fridge로 잡힘. 프론트는 `inFridge:false`인 재료 옆에 "장바구니에 담기" 버튼을 두고, 누르면 그 `ingredientNameText`로 바로 6번(장보기 추가) API를 호출하면 됨 — 별도의 "레시피 재료→장바구니" 전용 API는 만들지 않음(기존 장보기 추가 API 재사용).

## 사진으로 재료 자동 등록 — 1단계: 영수증 인식

원래 앱 기획에 있던 "사진으로 재료 등록" 3가지 방법(영수증 사진 / 쿠팡·네이버 구매내역 캡처 / 실물 상품 사진) 중 첫 번째. 나머지 둘은 미착수.

- `domain/receipt/` 신규 패키지. `POST /fridges/{fridgeId}/receipts/scan` — `multipart/form-data`로 영수증 이미지(JPEG/PNG/WEBP, 최대 10MB)를 받아 Claude **비전**에게 분석시켜 식재료 후보 목록(이름/수량/카테고리 추정)을 뽑아준다.
- **이 API는 아무것도 저장하지 않는 순수 미리보기**다 — 영수증 항목명은 "국산돈목심600"처럼 축약/코드화되어 있어서 그대로 자동 등록하면 안 되고(사용자 지시: 완전 자동보다 확인 단계 필요), 인식 결과를 프론트가 보여주고 사용자가 확인/수정한 뒤 **기존** `POST /ingredients`(4.4) + `POST /fridges/{fridgeId}/items`(3.1)를 그대로 호출해서 등록하는 구조로 설계함 — 새로 "일괄 등록" API를 만들지 않고 이미 검증된 두 엔드포인트를 재사용.
- 비전 지원을 위해 `common/client/anthropic/ClaudeMessageRequest.Message.content`를 `String`에서 `Object`로 바꾸고, `Message.withImage(role, text, mediaType, base64Data)` 팩토리 메서드를 추가함(text+image content block 배열). 기존 `ClaudeNutritionClient`/`ClaudeRecipeClient`는 여전히 `new Message("user", 문자열)`을 그대로 쓰므로 호환됨 — String도 Object라서 컴파일에 영향 없음.
- 새 클라이언트 `domain/receipt/external/ClaudeReceiptClient`: 텍스트 프롬프트 없이 이미지만 보내는 게 아니라 "영수증에서 식재료만 골라 이름/수량/카테고리를 추출해달라"는 프롬프트 + 이미지를 함께 보내고, `record_receipt_items` tool-use로 구조화된 결과를 받음(기존 `ClaudeNutritionClient`/`ClaudeRecipeClient`와 동일한 tool-use 패턴).
- 이름 매칭: `IngredientService`에 `matchByName(name)`(정확 일치 → 부분 일치 순) public 메서드를 새로 뽑아냄 — 기존에 `RecipeService`에 똑같은 로직이 private으로 중복되어 있었는데, `ReceiptService`까지 세 번째로 똑같이 베끼는 대신 `IngredientService`(식재료 매칭의 자연스러운 소유자)로 옮기고 `RecipeService.matchIngredient()`는 이걸 위임 호출하도록 리팩터링함.
- 로컬 검증: PowerShell `System.Drawing`으로 가짜 영수증 이미지(품목 5개)를 만들어 스캔 → 실제로 항목/카테고리 추정 정상 인식 → 인식된 이름으로 `POST /ingredients`(영양정보 자동 추정까지) → `POST /fridges/{fridgeId}/items` 등록까지 end-to-end 확인 완료.
- 겪은 삽질: Windows Git Bash curl에서 `-F "image=@경로;type=image/png"`처럼 `;type=`을 붙이면 `exit 26`으로 파일을 못 읽는다고 나옴(원인 불명, 다른 호스트로는 정상 업로드됨) — `;type=` 빼고 `-F "image=@경로"`만 쓰면 정상(위 "기술적 특이사항" 섹션에도 기록).

## 다음 할 일 후보 (우선순위 순 아님, 상황 보고 정하기)

- `FridgeItem`/`Recipe`/장보기 API들의 냉장고 멤버 권한 검증 (지금은 냉장고 존재 여부만 확인)
- 프론트엔드의 "영양정보 동기화" 버튼 — 이제 없는 엔드포인트(`/ingredients/sync-raw-materials`)를 호출하고 있어서 프론트에서 제거 필요
- 기존에 영양정보 없이 등록된 재료들(예: 계란/목살/양파/소금 등)을 일괄로 재추정하는 백필(backfill) 기능 (요청은 있었으나 미구현)
- 소셜 로그인 / 이메일 인증 / 비밀번호 재설정 / 로그아웃 시 JWT 즉시 무효화
- 식단 기록(MealLog) API
- **사진으로 재료 등록 2/3단계**: 쿠팡/네이버 구매내역 캡처 화면 인식, 실물 상품 사진 인식(예: 만두 포장 사진 → 이름/카테고리 자동 인식) — 둘 다 방금 만든 `ClaudeReceiptClient`/비전 패턴을 그대로 확장하면 됨
