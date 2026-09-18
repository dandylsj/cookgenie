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
MFDS_PROCESSED_FOOD_API_KEY: data.go.kr에서 발급받은 "전국통합식품영양성분정보(가공식품)" 서비스키(디코딩 키)
MFDS_DISH_API_KEY: data.go.kr에서 발급받은 "전국통합식품영양성분정보(음식)" 서비스키(디코딩 키) — 계정이 같으면 위 키와 같은 값일 수 있음
KAKAO_REST_API_KEY: 카카오 디벨로퍼스에서 발급받은 REST API 키
KAKAO_CLIENT_SECRET: 카카오 디벨로퍼스에서 "Client Secret"을 활성화했을 때만 필요, 기본은 비워둬도 됨
GOOGLE_CLIENT_ID: Google Cloud Console에서 발급받은 OAuth 2.0 클라이언트 ID(웹 애플리케이션 타입)
GOOGLE_CLIENT_SECRET: 위 클라이언트와 함께 발급되는 클라이언트 보안 비밀번호
```

이 파일만 만들어두면 IDE/터미널에 별도 환경변수를 설정하지 않아도 로컬에서 바로 실행됩니다. (env var로 덮어쓰고 싶으면 OS 환경변수로 `DB_PASSWORD`/`JWT_SECRET_KEY`/`ANTHROPIC_API_KEY`/`YOUTUBE_API_KEY`/`COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY`/`MFDS_PROCESSED_FOOD_API_KEY`/`MFDS_DISH_API_KEY`/`KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`를 설정해도 동일하게 동작 — Spring이 어차피 이름이 같은 프로퍼티로 플레이스홀더를 채움).

`YOUTUBE_API_KEY`는 Google Cloud Console에서 **YouTube Data API v3**를 활성화하고 발급받은 API 키입니다(무료지만 일일 할당량 있음). 유튜브 레시피 검색/가져오기 기능에 쓰입니다.

`COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY`는 **쿠팡파트너스** 가입 후 발급받는 키입니다(HMAC 서명 인증). 값이 없거나 틀려도 앱은 정상 기동하고, `GET /coupang/search` 호출만 실패해서 빈 리스트를 반환합니다(크래시 안 남) — 로컬에서 이 기능을 안 쓸 거면 아무 문자열이나 넣어둬도 됨.

`MFDS_PROCESSED_FOOD_API_KEY`는 공공데이터포털(data.go.kr)에서 "전국통합식품영양성분정보(가공식품)표준데이터" 활용신청 후 받는 서비스키입니다. **디코딩(원본) 키**를 넣어야 함 — `MfdsProcessedFoodClient`가 직접 한 번만 URL 인코딩하므로 이미 인코딩된 키를 넣으면 이중 인코딩으로 인증 실패함. 값이 없거나 틀려도 앱은 정상 기동하고, 재료 등록 시 이 조회만 실패해서 기존처럼 Claude 추정으로 넘어갑니다(크래시 안 남) — 로컬에서 이 기능을 안 쓸 거면 아무 문자열이나 넣어둬도 됨.

`MFDS_DISH_API_KEY`는 같은 data.go.kr 계정으로 "전국통합식품영양성분정보(음식)표준데이터"를 별도로 활용신청해서 받는 서비스키입니다(가공식품과 완전히 다른 API/데이터셋이라 활용신청 자체는 따로 필요 - 다만 data.go.kr 계정당 서비스키가 보통 하나라서 값 자체는 위 `MFDS_PROCESSED_FOOD_API_KEY`와 같을 수 있음). 마찬가지로 디코딩 키를 넣어야 하고, 없거나 틀려도 크래시 없이 해당 조회만 실패함.

`KAKAO_REST_API_KEY`는 [카카오 디벨로퍼스](https://developers.kakao.com)에서 앱을 만들면 발급되는 REST API 키입니다(카카오 로그인의 OAuth `client_id`로 그대로 씀). 이 키가 없으면 앱 기동 자체가 실패함(다른 외부 API 키들과 달리 `${KAKAO_REST_API_KEY}`에 기본값이 없어서) — 로컬에서 카카오 로그인을 안 쓸 거면 아무 문자열이나 넣어두면 기동은 되고 `/auth/kakao` 호출만 실패함. `KAKAO_CLIENT_SECRET`은 콘솔의 "카카오 로그인 > 보안 > Client Secret"을 활성화한 경우에만 필요하고(`${KAKAO_CLIENT_SECRET:}`로 기본값이 빈 문자열이라 안 넣어도 기동은 됨), 활성화했는데 안 보내면 토큰 교환이 거부됨.

`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`은 [Google Cloud Console](https://console.cloud.google.com) → API 및 서비스 → OAuth 클라이언트 ID(유형: **웹 애플리케이션**)를 만들면 발급됩니다. 카카오와 달리 구글은 웹 클라이언트 타입에서 client_secret이 선택이 아니라 **항상 필요**해서(`GoogleAuthClient`가 토큰 교환 요청에 항상 포함시킴) 둘 다 기본값 없는 필수 프로퍼티임 — 둘 중 하나라도 없으면 앱 기동 자체가 실패함. OAuth 클라이언트를 만들 때 "승인된 리다이렉트 URI"에 실제 쓸 프론트 도메인 + `/auth/google/callback`을 등록해둬야 함(카카오의 Redirect URI 등록과 같은 개념).

`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`은 로컬 기본값(`localhost`/`3306`/`cookgenie`/`root`)이 있어서 별도 설정 없이 그대로 씁니다. 로컬 MySQL은 `sql/create_database.sql`로 `cookgenie` DB만 만들면 테이블은 앱 기동 시 자동 생성됩니다.

**배포 서버 쪽**은 `application-secrets.yml`이 이미지에 아예 없으므로(로컬 전용, git에도 안 올라가고 Docker 이미지에도 안 들어감) 관여하지 않고, `.github/workflows/deploy-to-ubuntu.yml`이 GitHub `ubuntu` 환경의 Secrets(`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `ANTHROPIC_API_KEY`, `YOUTUBE_API_KEY`, `COUPANG_ACCESS_KEY`, `COUPANG_SECRET_KEY`, `MFDS_PROCESSED_FOOD_API_KEY`, `MFDS_DISH_API_KEY`, `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GHCR_PAT`)에서 값을 읽어 서버의 `.env` 파일로 주입 → 컨테이너 실행 시 OS 환경변수로 전달되어 `application.yml`의 플레이스홀더를 채웁니다. **`COUPANG_ACCESS_KEY`/`COUPANG_SECRET_KEY`/`MFDS_PROCESSED_FOOD_API_KEY`/`MFDS_DISH_API_KEY`/`KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`는 GitHub `ubuntu` 환경 Secrets에 아직 등록 안 되어 있을 수 있으니 배포 전에 확인 필요.**

### 지나간 사고: 시크릿을 등록 안 해도 배포가 "성공"으로 뜨는 문제 (카카오 로그인에서 실제로 겪음)

`KAKAO_REST_API_KEY`/`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`처럼 `application.yml`에 기본값 없이 선언된(`${KAKAO_REST_API_KEY}`처럼 `:` 뒤에 아무것도 없는) 필수 프로퍼티는, **로컬**에서 `application-secrets.yml`에 아예 안 넣으면 그 환경변수 자체가 없어서 Spring이 기동 시점에 바로 크래시함(의도대로 동작). 하지만 **배포 워크플로우**에서는 `echo "KAKAO_REST_API_KEY=${{ secrets.KAKAO_REST_API_KEY }}" >> .env`처럼 항상 그 줄 자체는 쓰기 때문에, GitHub Secrets에 값을 등록 안 해놔도 빈 문자열(`""`)로 치환되어 `.env`에 `KAKAO_REST_API_KEY=`(빈 값)가 그대로 들어감 — 컨테이너 입장에서는 "환경변수가 없는 게 아니라 빈 문자열인 상태"라서 Spring의 필수 프로퍼티 검증을 통과해버리고 앱이 정상 기동함. 그 결과 배포는 계속 "성공"으로 뜨지만, 실제로는 빈 `client_id`로 카카오/구글에 요청을 보내게 되어 로그인 시도 시에만(401 등으로) 실패가 드러남. **그래서 "배포가 성공했다" = "시크릿이 등록되어 있다"가 절대 아니고, 실제로 그 기능을 써봐야(로그인 시도) 시크릿이 제대로 들어갔는지 확인된다** — 새 소셜 로그인 provider를 추가할 때마다 이 함정을 기억할 것.

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
| Auth | 완료 — 회원가입/로그인/로그아웃/탈퇴/토큰재발급/프로필 + **게스트 시작/게스트→정식회원 전환** + **카카오/구글 로그인**. JWT, Spring Security |
| Fridge | 완료 — 생성/목록/단건조회/삭제(OWNER만) + **4자리 초대코드 발급/참여로 공유** |
| FridgeItem | 완료 — CRUD, 재료 수량 기준 탄단지 자동 계산(단위 일치할 때만) |
| Ingredient | 완료 — 검색/등록/수정/삭제. **등록 시 Claude가 100g 기준 영양정보 자동 추정** (아래 참고) |
| Recipe | **1, 2단계 완료**: AI 레시피 생성(냉장고 재료 기반), 유튜브 레시피 검색/가져오기, 재료 기반 레시피 추천, 목록/상세/삭제. **각 재료의 냉장고 보유 여부(inFridge)도 계산** |
| Receipt/Product(사진 인식) | **3/3단계 완료**: 영수증 사진 / 온라인 쇼핑몰 주문내역 캡처 / 실물 상품 사진 → Claude 비전으로 식재료 후보 추출(미리보기만, 저장은 안 함), **기존 재료·가공식품·음식 공식 데이터와 자동 매칭**까지 포함 |
| Shopping(장보기) | 완료 — 냉장고별 장보기 리스트 추가/조회/체크/삭제, **쿠팡파트너스 연동 최저가 검색** |
| MealLog(식단 기록) / NutritionGoal | **백엔드 완료** — 기록 추가(레시피 선택/재료 직접입력)/하루 상세 조회/달력 요약/삭제, 목표 칼로리·탄단지 설정/조회. **프론트는 미착수** |
| 이메일 인증 / 비밀번호 재설정 | 미구현 (카카오/구글 소셜 로그인은 완료, 아래 참고) |

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
3. **3차 수정 — 진짜 웹 검색 추가**: 등록은 되는데(`isValidFood=true`) 실제 값과 다르다는 피드백을 받음 — 애초에 이 클라이언트는 **웹 검색을 전혀 안 하고 Claude의 학습된 지식만으로 "추정"**하는 구조였음(그래서 특정 브랜드 제품의 정확한 포장지 영양정보와는 다를 수밖에 없었음). Anthropic Messages API의 서버사이드 `web_search` 도구(`web_search_20250305` — Haiku는 최신 동적 필터링 버전인 `web_search_20260209`을 지원 안 해서 기본형을 씀)를 `record_nutrition_estimate`와 함께 tools에 추가하고, `tool_choice`를 강제 호출(`{"type":"tool",...}`)에서 `{"type":"auto"}`로 바꿔서 Claude가 브랜드+상품명이 있는 이름은 먼저 검색해보고, 검색으로 못 찾으면 기존처럼 추정하도록 함. `max_tokens`도 검색 결과가 응답에 섞여 들어갈 걸 감안해 300→1500으로 늘림. **아직 실제 API로 검증 못 함.** 검색이 추가되면 호출당 지연시간/비용이 늘어난다는 점 참고(Anthropic 웹 검색은 사용 건당 별도 과금).
4. **4차 수정 — 식약처 가공식품 공공데이터를 AI보다 먼저 조회**: 사용자가 식약처 "전국통합식품영양성분정보(가공식품)표준데이터" API(data.go.kr, 원본 CSV는 31만 건·130MB가 넘어서 번들 불가) 활용신청을 받아옴. AI 추정(웹검색 곁들여도 결국 "추정")보다 정부가 실측한 이 데이터가 더 정확하므로, `MfdsProcessedFoodClient.search(name)`을 만들어서 **`IngredientService.createIngredient()`에서 Claude 추정보다 먼저 시도**하도록 함(매칭되면 `dataSource=OFFICIAL_DB`, `isVerified=true`, `ingredientType=PROCESSED`, 못 찾으면 기존처럼 Claude로 폴백). `CoupangProductClient`에서 겪었던 것과 같은 이중 URL 인코딩 문제를 피하려고 `URI.create()`로 미리 인코딩한 URI를 만들어 넘김. `MFDS_PROCESSED_FOOD_API_KEY`는 반드시 **디코딩(원본) 키**를 써야 함(환경설정 섹션 참고).
   - **배포 후 실제 테스트에서 발견된 버그(수정함)**: 기준량(`nutConSrtrQua`)이 정확히 "100g/100ml"인 항목만 인정하고 나머지는 버리는 필터링이 있었는데, 실제 데이터를 보니 1회 제공량·포장 전체 중량 등 기준량이 제각각인 항목이 훨씬 많아서 흔한 가공식품 대부분이 걸러져 칼로리가 전혀 안 잡히는 문제가 있었음. → 100이 아니면 버리는 대신, 어떤 기준량이든 100 기준으로 **비례 환산**해서 항상 우리 스키마(`referenceAmount=100`)에 맞춰 반환하도록 수정.
5. **5차 수정 — 자동 매칭 대신 검색해서 직접 고르는 UI 추가**: `foodNm`에는 브랜드명이 안 들어있는 경우가 많아서(예: "요거트 아이스크림"이라는 같은 이름으로 제조사가 다른 상품이 여럿 존재) 등록 시점에 그냥 "첫 매칭"을 자동으로 쓰는 방식은 사용자가 기대한 제품이 아닌 게 저장될 수 있음. 그래서 자동 매칭은 그대로 두고(폴백용으로 유지), **`GET /ingredients/official-search?keyword=`를 새로 추가**해서 사용자가 "실온"→"닭"처럼 검색어를 좁혀가며 후보 목록(제품명+제조사+칼로리)을 직접 보고 정확한 걸 고를 수 있게 함. 프론트(`IngredientPicker`)에 "🏛️ 식약처 공식 가공식품 데이터에서 찾기" 진입점을 추가해서, 고른 후보의 값을 그대로 `POST /ingredients`의 직접 입력값(calories 등)으로 넘겨서 등록함(추가 AI/API 호출 없음).
   - **배포 후 실제 테스트에서 발견된 버그(5-1차 수정)**: 실제로 "실온" 검색이 "검색 결과가 없어요"로 항상 실패함. 도커 로그에 원본 응답을 남겨보니(`MfdsProcessedFoodClient`에 raw body 로깅 추가) `{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"}}`가 찍힘 — 이건 인증 실패가 아니라 **정상적인 "매칭 없음" 응답**이었고, 진짜 원인은 이 API의 `foodNm` 파라미터가 **완전 일치만 지원**한다는 것(사용자가 실제 제품 전체 이름으로 브라우저에서 직접 테스트해서 확인). 그래서 애초에 "실온"이라는 부분 문자열로는 절대 못 찾음 — 삼성헬스처럼 몇 글자만 쳐도 후보가 뜨는 UX는 이 API 자체로는 만들 수 없는 구조였음.
   - **5-2차 수정 (현재) — 전체 데이터를 로컬 DB로 미러링**: `foodNm`을 아예 빼고 페이지만 넘기면 필터 없이 전체 데이터를 순서대로 다 받을 수 있다는 걸 확인함(사용자가 브라우저로 직접 확인, `totalCount:590542`, `resultCode:00`). 그래서 정부 API를 직접 매번 부르는 대신, 앱 기동 후 관리자가 한 번 트리거하면 전체(약 59만 건, 1000건씩 약 591페이지)를 새 테이블 `official_processed_foods`(`OfficialProcessedFood` 엔티티)로 복사해두고, `GET /ingredients/official-search`는 이제 이 로컬 테이블에서 `LIKE` 부분검색을 하도록 바꿈(`OfficialProcessedFoodRepository.findByFoodNmContainingOrMfrNmContaining`). 이제 진짜로 "실온"→"닭"처럼 좁혀가는 부분검색이 됨.
     - 새 엔드포인트 `POST /ingredients/official-foods/sync?force=`(`OfficialProcessedFoodSyncService`) — 이미 데이터가 있으면 스킵(현재 건수만 알려줌), `force=true`면 전체를 다시 훑어서 누락된 항목만 추가로 채움(기존 데이터는 지우지 않음). 정부 API 순차 호출이 몇 분 걸릴 수 있어서 `@Async`(`CookgenieApplication`에 `@EnableAsync` 추가)로 실행하고 트리거 요청은 시작 여부만 즉시 응답함. 같은 빈 안에서 자기 자신을 호출하면 `@Async` 프록시를 안 타서(self-invocation 문제) 컨트롤러가 동기 체크(`prepareSync`)와 비동기 실행(`runSync`)을 별도 메서드로 나눠서 호출함.
     - `MfdsProcessedFoodClient`에도 그동안 안 걸렸던 버그가 있었음 — JSON 파싱이 `root.path("response").path("header"/"body")`처럼 `response`로 한 번 더 감싸진 구조를 가정했는데, 실제 응답은 `{"header":..,"body":..}`로 평평함. 페이지 수집 기능을 추가하면서 같이 고침(`parseCandidates`/새 `fetchPage`/`parsePage` 전부 공통 헬퍼로 정리).
     - `IngredientService.createIngredient()`의 등록 시점 자동 매칭은 이제 로컬 테이블을 먼저 보고(빠름, 오프라인), 못 찾으면(동기화 전이거나 그 테이블에 없는 이름) 기존처럼 정부 API 완전 일치 검색으로 한 번 더 보완 시도함(`lookupOfficialEstimate`).
     - **배포 후 반드시 한 번 `POST /ingredients/official-foods/sync`를 호출해서 데이터를 채워야** 부분검색이 실제로 동작함(안 채우면 늘 빈 목록) — 앱이 알아서 시작하지 않음, 몇 분 걸리는 걸 감안해 수동 트리거로 남겨둠.
     - **배포 후 실제 실행에서 발견된 문제(5-3차 수정)**: 첫 실행에서 `Duplicate entry` 예외로 pageNo=4에서 죽는 버그가 있어서 위처럼 페이지 간 중복을 걸러내도록 고쳤는데(이 항목 참고), 그 고침을 배포하고 다시 돌려보니 이번엔 죽지는 않고 591페이지를 끝까지 다 돌긴 했지만 **59만 건 중 245,668건만 저장됨**. `force=true`(당시엔 "지우고 재시작"이 아니라 "누락분만 추가로 채우기"로 이미 바꿔둔 상태)로 다시 돌려봤더니 591페이지를 또 끝까지 돌았는데 **이번엔 새로 저장된 게 0건**이었음 — 이 결과가 중요한 단서였음. 페이지네이션이 매번 다른 부분집합을 보여주는 "불안정한" 것이었다면 재실행 때 최소 몇 건은 새로 나왔어야 하는데 정확히 0건이라는 건, 오히려 **같은 페이지를 다시 호출하면 항상 같은 데이터가 나온다(안정적)**는 뜻이었음.
   - **5-4차 수정 (진짜 원인) — foodCd는 상품별 고유 코드가 아니다**: 다시 살펴보니 이미 이 저장소에 있던 다른 동기화 코드(`OfficialNutritionSyncService`, CSV 기반의 별개 기능)의 주석에 "표준데이터를 **대표식품코드 기준으로 묶어서**" 처리한다고 명시돼 있었음 - 즉 이 코드체계 자체가 원래 1:1이 아니라 여러 제조사/상품이 하나의 대표코드를 공유하는 구조. `foodCd` 하나만으로 유니크 제약을 걸었더니, 서로 다른 제조사/상품인데 같은 `foodCd`를 가진 항목들이 전부 "이미 있음"으로 걸러져 버린 것이 진짜 원인이었음(590,542행 → 245,668건으로 줄고, 재실행해도 0건만 나온 게 전부 이걸로 설명됨). `OfficialProcessedFood`의 유니크 제약을 `food_cd` 단독에서 `(food_cd, food_nm, mfr_nm)` 조합으로 바꾸고, `OfficialProcessedFoodSyncService.saveBatch()`의 중복 판단도 같은 조합 키로 바꿈.
     - **배포 시 주의**: 서버 DB에 이미 옛 스키마(`food_cd` 단독 유니크 인덱스)로 245,668건이 저장되어 있어서, `ddl-auto: update`가 옛 유니크 인덱스를 알아서 지워주지 않을 수 있음. 배포 후 `ALTER TABLE official_processed_foods DROP INDEX idx_official_processed_food_code;`를 한 번 실행해서 옛 인덱스를 지워야 새 조합 유니크 제약이 정상적으로 추가되고, 그래야 `POST /ingredients/official-foods/sync?force=true`를 다시 돌렸을 때 누락된(다른 제조사의 같은 대표코드) 상품들이 채워짐.
6. **6차 — "음식"(배달/외식 메뉴) 공공데이터 추가**: 가공식품 데이터는 포장 제품 위주라 "짜장면", "치킨 1인분"처럼 배달/외식으로 먹는 조리된 메뉴의 칼로리·탄단지 정보에는 안 맞음. 식약처가 별도로 제공하는 **"전국통합식품영양성분정보(음식)표준데이터"**(`tn_pubr_public_nutri_food_info_api`, 국민건강영양조사 음식별 식품재료량 자료집 기반)를 5-2~5-4차와 완전히 같은 구조로 한 벌 더 만듦 - `MfdsDishClient`/`OfficialDish`(테이블 `official_dishes`)/`OfficialDishRepository`/`OfficialDishSyncService`, `GET /ingredients/dish-search`, `POST /ingredients/dishes/sync?force=`. 가공식품과 헷갈리지 않게 코드 전체에서 "Dish"로 이름을 분리함.
   - 이 API는 요청변수 목록에 `foodCd`(식품코드)와 `foodLv4Cd`/`foodLv4Nm`(대표식품코드/대표식품명)이 **별개 필드로 분리**되어 있어서, 가공식품에서처럼 `foodCd`가 대표코드를 겸하는 구조는 아닐 가능성이 높음 - 그래도 가공식품에서 그 가정이 틀렸던 적이 있어서, 처음부터 안전하게 `OfficialDish`의 유니크 제약을 `food_cd` 단독이 아니라 `(food_cd, food_nm, rest_nm)` 조합으로 걸고 `saveBatch()`도 같은 조합 키로 중복을 판단하게 만듦(이번엔 재발 방지를 처음부터 반영, 배포 후 실측으로 재확인 필요).
   - 영양성분 필드명(`enerc`/`chocdf`/`prot`/`fatce`/`sugar`/`nat`/`fibtg`/`nutConSrtrQua`)은 가공식품 API와 완전히 동일해서 `MfdsProcessedFoodClient`의 파싱/정규화 로직을 그대로 복사해서 재사용함. 브랜드명 역할을 하는 필드만 가공식품은 `mfrNm`(제조사)이고 음식은 `restNm`(제공 업체명)으로 다름 - 개념이 달라서 `OfficialFoodCandidate`(가공식품)와 별도로 `OfficialDishCandidate`(음식)를 만듦.
   - `IngredientService.createIngredient()`의 `lookupOfficialEstimate()`가 이제 가공식품 → 음식 순으로 로컬 미러 + 정부 API 완전일치를 체인으로 시도함(하나라도 매칭되면 그 값을 씀, `ingredientType`은 둘 다 편의상 `PROCESSED`로 분류 - RAW/PROCESSED 두 값뿐이라 "조리된 음식"에 더 가까운 쪽을 씀).
   - `MFDS_DISH_API_KEY` 환경변수 필요(환경설정 섹션 참고, 같은 data.go.kr 계정이면 가공식품 키와 같은 값일 수 있음). **배포 후 `POST /ingredients/dishes/sync`를 한 번 호출해야** `GET /ingredients/dish-search` 부분검색이 실제로 동작함.

## 게스트 로그인 (회원가입 없이 바로 시작)

첫 사용자의 진입장벽을 낮추기 위해, 회원가입 없이 바로 냉장고/재료/레시피 기능을 다 써볼 수 있는 게스트 모드를 추가함.

- `POST /auth/guest` — 인증 불필요, body 없음. `User.provider="GUEST"`(원래 소셜로그인용으로 만들어뒀던 컬럼을 재활용), 자동 생성된 고유 이메일/아이디로 진짜 유저 row를 만들고 즉시 access/refresh 토큰 발급. 이후 냉장고 생성/재료 등록/AI 레시피 생성 등 **일반 회원과 완전히 동일하게** 동작함(로컬 스토리지에 데이터를 따로 들고 있는 방식이 아니라 서버에 진짜 계정을 만드는 방식 — 영양정보 추정/AI 레시피 생성이 어차피 서버 호출이 필요해서 이렇게 설계함).
- `POST /auth/guest/upgrade` — 인증 필요(게스트 토큰). body는 회원가입과 동일(`SignupRequest`). **같은 유저 id를 그대로 승격**시키는 방식이라 게스트로 쌓아둔 냉장고/재료 데이터가 이관 없이 그대로 유지됨. 성공 시 새 토큰을 발급하므로 프론트는 기존 게스트 토큰을 새 토큰으로 교체해야 함.
- `User.upgradeFromGuest()`가 loginId/password/email/nickname을 채우고 provider/providerId를 null로 지워서 이후 `signup()`의 소셜/게스트 판별 로직과 충돌하지 않게 함.
- **3일 미전환 시 자동 삭제**: `GuestCleanupScheduler`(`@Scheduled(cron="0 0 * * * *")`, 매시 정각)가 `provider="GUEST"`이고 `createdAt`이 `User.GUEST_RETENTION_DAYS`(=3일)보다 오래된 유저를 찾아서, 소유한 Fridge/FridgeItem/ShoppingItem/FridgeMember와 RefreshToken까지 함께 정리(FK 제약 때문에 `FridgeService.deleteFridge()`와 동일한 순서: FridgeItem→ShoppingItem→FridgeMember→Fridge→User)한 뒤 유저 자체를 삭제함. `CookgenieApplication`에 `@EnableScheduling` 추가함(원래 없었음, MFDS 배치 스케줄러 제거할 때 `SchedulingConfig`도 같이 지웠었음).
- **프론트 공지용 정보**: `GET /auth/profile`(`UserInfoResponse`)에 `guest`(boolean)와 `guestExpiresAt`(게스트일 때만 값 있음 = `createdAt + 3일`) 필드를 추가함. 프론트에서 게스트 로그인 직후 또는 프로필 조회 시 이 값으로 "n일 후 데이터가 삭제됩니다 — 지금 회원가입하고 이어가기" 같은 배너를 띄우면 됨.
- 로컬에서 게스트 생성 → 냉장고 생성 → 게스트→회원 전환 → 새 토큰으로 로그인까지 curl로 end-to-end 검증 완료.

## 카카오 소셜 로그인

웹/앱 양쪽에서 재사용 가능하도록 설계함(모바일 앱을 나중에 만들 때도 이 백엔드를 그대로 재사용 — 클라이언트가 카카오 access token/인가 코드를 얻는 방법만 플랫폼별로 다르고, 그 이후 서버 검증/JWT 발급 로직은 동일함).

- **인가 코드(authorization code) 방식**으로 구현함(카카오 JS SDK 없이도 동작 — REST API 키만 있으면 됨). 흐름: 프론트가 사용자를 `https://kauth.kakao.com/oauth/authorize?client_id={REST_API_KEY}&redirect_uri=...&response_type=code`로 보냄 → 카카오 로그인/동의 후 그 `redirect_uri`로 `code`와 함께 리다이렉트됨 → 프론트가 `code`+`redirectUri`를 `POST /auth/kakao`로 보냄 → 백엔드가 카카오 토큰 엔드포인트로 `code`를 액세스 토큰으로 교환하고, 그 토큰으로 `/v2/user/me`를 호출해 사용자 정보를 받아옴.
- 새 클라이언트 `domain/auth/external/KakaoAuthClient`(토큰 교환 + 사용자 조회, 실패하면 `ErrorMessage.KAKAO_LOGIN_FAILED` 던짐 — 다른 외부 클라이언트들(Claude/유튜브/쿠팡)과 달리 로그인 자체가 목적이라 "실패 시 빈 값 반환" 패턴을 안 쓰고 예외를 던짐)과 `domain/auth/external/KakaoUserInfo`(id/nickname record).
- `AuthService.kakaoLogin()`이 `User.findByProviderAndProviderId("KAKAO", kakaoId)`로 기존 계정을 찾거나, 없으면 새로 만듦(이미 게스트/소셜 계정 로직에서 쓰던 `provider`/`providerId` 컬럼과 `findByProviderAndProviderId` 리포지토리 메서드를 그대로 재사용 — 이 컬럼들이 원래 소셜로그인용으로 만들어뒀던 것이라 새 마이그레이션 불필요).
- **카카오 이메일은 안 받음**: `kakao_account.email`은 별도 비즈 심사 없이는 대부분 제공되지 않고, 설령 받아온다 해도 그 이메일로 기존 로컬 회원가입 계정과 자동 연결하는 건 하지 않기로 함 — 이메일 소유 확인 없이 카카오 로그인 한 번으로 다른 사람의 기존 계정에 접근할 수 있게 되는 보안 문제가 생기기 때문. 대신 신규 카카오 계정은 내부용으로만 쓰이는 고유 이메일(`kakao_{카카오id}@cookgenie.social`)을 받고, 닉네임은 카카오 프로필 닉네임(없으면 "카카오 사용자")을 씀.
- `User.KAKAO_PROVIDER = "KAKAO"` 상수 추가(`GUEST_PROVIDER`와 같은 패턴).
- 환경변수 `KAKAO_REST_API_KEY`(필수, 카카오 디벨로퍼스에서 발급) / `KAKAO_CLIENT_SECRET`(선택, 콘솔에서 Client Secret을 활성화했을 때만) 추가 — 환경설정 섹션 참고. `SecurityConfig`의 `PUBLIC_URLS`에 `/auth/kakao` 추가.
- **프론트(cookgenieWeb)**: `LoginPage`에 "카카오로 로그인" 버튼 추가(클릭하면 카카오 인가 페이지로 리다이렉트) + 새 라우트 `/auth/kakao/callback`(`KakaoCallbackPage`, 비로그인 상태에서도 접근 가능해야 해서 `ProtectedRoute` 밖에 둠)에서 `?code=`를 받아 `POST /auth/kakao` 호출 → 성공하면 홈으로 이동. `redirect_uri`는 도메인을 하드코딩하지 않고 `${window.location.origin}/auth/kakao/callback`으로 동적으로 만들어서(`utils/kakao.js`) 로컬/프리뷰/프로덕션 어디서든 같은 코드로 동작하게 함 — 그래서 **카카오 디벨로퍼스 콘솔의 "Redirect URI"에 실제 쓰는 도메인마다(예: `http://localhost:5173/auth/kakao/callback`, 배포 도메인 `.../auth/kakao/callback`) 전부 등록해둬야 함** (안 하면 카카오가 `redirect_uri mismatch`로 거부함 — 사용자가 직접 콘솔에서 해야 하는 수동 설정).
- REST API 키는 카카오 로그인 방식상 프론트 코드(JS)에 그대로 노출되는 값이라 민감정보 취급 안 해도 됨(공식적으로 클라이언트에 노출되도록 설계된 값) — `VITE_KAKAO_REST_API_KEY`로 프론트 `.env.development`에 추가함(로컬용, 실제 키 값은 각자 채워야 함). 배포(Vercel)는 프로젝트 환경변수 설정에서 별도로 등록 필요.
- **실제 배포 후 end-to-end 검증 완료** — 그 과정에서 겪은 문제들과 원인:
  1. **KOE006 "앱 관리자 설정 오류"**: 카카오 디벨로퍼스 콘솔에서 "카카오 로그인" 제품 활성화 + 플랫폼(Web) 도메인 등록 + Redirect URI 등록, 이 세 가지를 다 해야 함 — 하나라도 빠지면 이 에러가 남.
  2. **콜백 페이지가 404**: Vercel이 `vercel.json` 없이는 SPA 라우팅 fallback이 없어서, 앱 안에서 `<Link>`로 이동할 때는 문제없지만 카카오처럼 외부에서 풀 페이지로 리다이렉트되는 경로(`/auth/kakao/callback`)는 실제 파일이 없다며 404가 남. 모든 경로를 `index.html`로 rewrite하는 `vercel.json` 추가로 해결.
  3. **브라우저에 CORS 에러로 보였던 502**: 실제로는 위 "지나간 사고"에 적은 대로 `KAKAO_REST_API_KEY` GitHub Secret이 등록 안 돼 있어서(빈 문자열) 카카오가 401을 준 것이었음 — CORS 헤더가 없는 에러 응답이라 브라우저가 CORS 문제로 잘못 표시함. `docker logs`로 서버 로그를 직접 봐야 진짜 원인(401)이 드러남.
  4. **키를 다시 넣어도 계속 401**: REST API 키 자체는 맞았지만, 콘솔에서 "Client Secret"이 활성화돼 있던 상태라 `KAKAO_CLIENT_SECRET`도 같이 등록해야 했음.
  5. **닉네임이 항상 "카카오 사용자"로만 저장됨**: 인가 요청에 `scope`를 안 보내면 콘솔 동의항목이 "필수 동의"가 아닌 항목(닉네임)은 안 내려옴 — `scope=profile_nickname`을 인가 URL에 명시해서 해결.

## 구글 소셜 로그인

카카오와 완전히 같은 인가 코드(authorization code) 구조로 만듦 — 위 카카오 섹션에서 겪은 시행착오(콘솔 설정 누락, 시크릿 미등록, scope 누락 등)를 미리 반영해서 처음부터 정리된 상태로 구현함.

- 흐름은 카카오와 동일: 프론트가 `https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&response_type=code&scope=openid email profile`로 리다이렉트 → 구글 로그인/동의 후 `code`와 함께 `redirect_uri`로 리다이렉트됨 → 프론트가 `code`+`redirectUri`를 `POST /auth/google`로 보냄 → 백엔드가 `https://oauth2.googleapis.com/token`에서 액세스 토큰으로 교환하고, `https://www.googleapis.com/oauth2/v3/userinfo`로 사용자 정보(`sub`/`name`/`email`)를 받아옴.
- 새 클라이언트 `domain/auth/external/GoogleAuthClient`(`KakaoAuthClient`와 완전히 같은 구조 — 실패 시 예외, connect 5초/read 8초 타임아웃 처음부터 반영)와 `domain/auth/external/GoogleUserInfo`(id/nickname record).
- **구글은 client_secret이 선택이 아니라 항상 필수**임(웹 애플리케이션 클라이언트 타입 기준) — 카카오는 콘솔에서 Client Secret을 켰을 때만 필요했지만, 구글은 `GoogleAuthClient`가 토큰 교환 요청에 항상 `client_secret`을 포함시킴. `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` 둘 다 기본값 없는 필수 프로퍼티.
- `AuthService.googleLogin()`이 `User.findByProviderAndProviderId("GOOGLE", sub)`로 기존 계정을 찾거나 새로 만듦. **구글은 이메일을 항상 제공하지만**(카카오와 달리 비즈 심사 불필요), 카카오와 같은 이유(이메일 소유 확인 없이 로컬 계정에 자동 연결되는 보안 문제 — `email` 컬럼 유니크 제약과도 충돌함)로 실제 이메일을 쓰지 않고 내부용 고유 이메일(`google_{구글 고유 id}@cookgenie.social`)을 만들어 저장함 — 카카오와 완전히 같은 정책.
- `User.GOOGLE_PROVIDER = "GOOGLE"` 상수 추가. `SecurityConfig`의 `PUBLIC_URLS`에 `/auth/google` 추가.
- **프론트(cookgenieWeb)**: `LoginPage`에 "구글로 로그인" 버튼, 새 라우트 `/auth/google/callback`(`GoogleCallbackPage`, `KakaoCallbackPage`와 동일 구조), `utils/google.js`(`getGoogleAuthorizeUrl`/`getGoogleRedirectUri`, 카카오와 동일하게 `redirect_uri`를 `window.location.origin` 기준으로 동적 생성). **Google Cloud Console의 OAuth 클라이언트 "승인된 리다이렉트 URI"에 실제 쓰는 프론트 도메인마다 `/auth/google/callback`을 등록해둬야 함**(카카오의 Redirect URI 등록과 같은 개념 — 안 하면 `redirect_uri_mismatch` 에러).
- 클라이언트 ID는 카카오 REST API 키와 마찬가지로 프론트 코드에 노출되는 게 정상인 값이라 `VITE_GOOGLE_CLIENT_ID`로 프론트 `.env.development`에 추가함(로컬용). **클라이언트 시크릿(`GOOGLE_CLIENT_SECRET`)은 절대 프론트에 노출하면 안 됨** — 백엔드 시크릿으로만 등록.
- **실제 구글 OAuth 클라이언트로 end-to-end 검증 완료** — Google Cloud Console에서 OAuth 클라이언트 생성 + 승인된 리다이렉트 URI 등록 + `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`를 로컬(`application-secrets.yml`)과 배포(GitHub `ubuntu` 환경 Secrets, `VITE_GOOGLE_CLIENT_ID`는 Vercel 환경변수)에 등록한 뒤 실제 로그인 성공.

### 버그: 소셜 로그인 계정이 2개 이상 쌓이면 `GET /auth/profile`이 500 (수정함)

구글 로그인 검증 중 발견. 카카오 로그인 하나만 테스트했을 때는 잘 되던 `GET /auth/profile`이, 구글 로그인까지 테스트하고 나니 500 Internal Server Error로 깨짐. 원인은 `AuthService.getUserInfo()`/`withdraw()`가 JWT의 `subject`(=`loginId`)를 꺼내 `userRepository.findByLoginId(loginId)`로 사용자를 조회하는 구조였는데, **카카오/구글 로그인으로 만들어진 계정은 `loginId` 컬럼을 채우지 않아서 항상 `null`**이라는 점을 놓쳤던 것. Spring Data JPA는 `findByLoginId(null)`을 `WHERE login_id IS NULL`로 번역하는데, 소셜 계정이 1개뿐일 때는 이게 우연히 정확히 1건만 매칭돼서 문제가 드러나지 않았고, 카카오 테스트 계정(loginId=null) + 구글 테스트 계정(loginId=null)이 동시에 존재하게 되자 2건이 매칭되어 `findByLoginId`가 단건 조회(`Optional`)에 쓰이는 Hibernate 쿼리가 `NonUniqueResultException`을 던지면서 500이 남. `getUserInfo()`/`withdraw()` 둘 다 JWT의 `userId` 클레임(`extractUserId`)으로 `userRepository.findById()`를 쓰도록 고쳐서(이미 `updateNickname`/`upgradeGuest`/`logout`은 이 패턴을 쓰고 있었음) `loginId`가 없는 계정과 무관하게 항상 정확히 그 유저만 조회되게 함.

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

원래 앱 기획에 있던 "사진으로 재료 등록" 3가지 방법(영수증 사진 / 쿠팡·네이버 구매내역 캡처 / 실물 상품 사진) 중 첫 번째.

- `domain/receipt/` 신규 패키지. `POST /fridges/{fridgeId}/receipts/scan` — `multipart/form-data`로 영수증 이미지(JPEG/PNG/WEBP, 최대 10MB)를 받아 Claude **비전**에게 분석시켜 식재료 후보 목록(이름/수량/카테고리 추정)을 뽑아준다.
- **이 API는 아무것도 저장하지 않는 순수 미리보기**다 — 영수증 항목명은 "국산돈목심600"처럼 축약/코드화되어 있어서 그대로 자동 등록하면 안 되고(사용자 지시: 완전 자동보다 확인 단계 필요), 인식 결과를 프론트가 보여주고 사용자가 확인/수정한 뒤 **기존** `POST /ingredients`(4.4) + `POST /fridges/{fridgeId}/items`(3.1)를 그대로 호출해서 등록하는 구조로 설계함 — 새로 "일괄 등록" API를 만들지 않고 이미 검증된 두 엔드포인트를 재사용.
- 비전 지원을 위해 `common/client/anthropic/ClaudeMessageRequest.Message.content`를 `String`에서 `Object`로 바꾸고, `Message.withImage(role, text, mediaType, base64Data)` 팩토리 메서드를 추가함(text+image content block 배열). 기존 `ClaudeNutritionClient`/`ClaudeRecipeClient`는 여전히 `new Message("user", 문자열)`을 그대로 쓰므로 호환됨 — String도 Object라서 컴파일에 영향 없음.
- 새 클라이언트 `domain/receipt/external/ClaudeReceiptClient`: 텍스트 프롬프트 없이 이미지만 보내는 게 아니라 "영수증에서 식재료만 골라 이름/수량/카테고리를 추출해달라"는 프롬프트 + 이미지를 함께 보내고, `record_receipt_items` tool-use로 구조화된 결과를 받음(기존 `ClaudeNutritionClient`/`ClaudeRecipeClient`와 동일한 tool-use 패턴).
- 이름 매칭: `IngredientService`에 `matchByName(name)`(정확 일치 → 부분 일치 순) public 메서드를 새로 뽑아냄 — 기존에 `RecipeService`에 똑같은 로직이 private으로 중복되어 있었는데, `ReceiptService`까지 세 번째로 똑같이 베끼는 대신 `IngredientService`(식재료 매칭의 자연스러운 소유자)로 옮기고 `RecipeService.matchIngredient()`는 이걸 위임 호출하도록 리팩터링함.
- 로컬 검증: PowerShell `System.Drawing`으로 가짜 영수증 이미지(품목 5개)를 만들어 스캔 → 실제로 항목/카테고리 추정 정상 인식 → 인식된 이름으로 `POST /ingredients`(영양정보 자동 추정까지) → `POST /fridges/{fridgeId}/items` 등록까지 end-to-end 확인 완료.
- 겪은 삽질: Windows Git Bash curl에서 `-F "image=@경로;type=image/png"`처럼 `;type=`을 붙이면 `exit 26`으로 파일을 못 읽는다고 나옴(원인 불명, 다른 호스트로는 정상 업로드됨) — `;type=` 빼고 `-F "image=@경로"`만 쓰면 정상(위 "기술적 특이사항" 섹션에도 기록).

## 사진으로 재료 자동 등록 — 2/3단계: 주문내역 캡처 + 실물 상품 사진 (+ 공식 데이터 자동 매칭)

나머지 두 방법(온라인 쇼핑몰 주문내역 캡처, 실물 상품 사진)을 1단계와 같은 비전+tool-use 패턴으로 추가함. 동시에 "인식된 이름을 기존 재료뿐 아니라 가공식품/음식 공식 데이터에도 자동 매칭해줄 수 있냐"는 요청을 반영해서, 세 인식 API 모두 매칭 결과를 확장함.

- **주문내역 캡처**: `POST /fridges/{fridgeId}/receipts/scan-order-history` — 영수증과 추출 스키마(`ReceiptScanResult`)가 완전히 동일해서 새 클라이언트를 만들지 않고 `ClaudeReceiptClient`에 `scanOrderHistory()` 메서드만 추가함(프롬프트만 "쿠팡/마켓컬리/네이버쇼핑 주문내역 화면 캡처"에 맞게 다르게 줌, tool 정의는 공유). `ReceiptService`도 공통 이미지 검증(`validateImage`)/응답 변환 로직을 추출해서 `scanReceipt()`/`scanOrderHistory()`가 나눠 씀.
- **실물 상품 사진**: 새 `domain/product/` 패키지 — `POST /fridges/{fridgeId}/products/scan`. 상품 포장/라벨 사진 한 장(또는 여러 상품이 함께 찍힌 사진)에서 브랜드명 포함 상품명(예: "오뚜기 진라면 매운맛")과 중량 표시를 읽어내는 프롬프트를 쓰는 새 클라이언트 `ClaudeProductClient`를 만듦 — 다만 추출 결과 타입은 `domain/receipt/external/ReceiptScanResult`를 그대로 재사용(이름/수량/카테고리라는 추출 스키마가 완전히 같아서 굳이 새 record를 안 만듦). `ProductService`는 인식된 이름 → 기존 재료/공식 데이터 매칭 로직을 `ReceiptService.toItemResponse()`(public으로 변경)를 그대로 호출해서 재사용함 - 입력 사진 종류만 다를 뿐 "이름으로 맞춰본다"는 로직 자체가 동일해서 도메인 경계를 넘어 재사용하는 걸 선택함(이 코드베이스에 이미 있던 `RecipeService`→`IngredientService.matchByName()` 위임과 같은 패턴).
- **공식 데이터 자동 매칭 확장**: `IngredientService`에 `matchProcessedFoodByName(name)`/`matchDishByName(name)`(둘 다 로컬 미러 완전 일치)을 추가하고, `ReceiptItemResponse`에 `matchedProcessedFood`/`matchedDish` 필드를 새로 붙임. `ReceiptService.toItemResponse()`가 이제 (1) `Ingredient` 마스터 → (2) 가공식품 로컬 미러 → (3) 음식 로컬 미러 순서로 매칭을 시도하고(이미 (1)에서 매칭됐으면 (2)(3)은 조회하지 않음), 매칭되면 그 값을 그대로 프론트가 `POST /ingredients`의 직접 입력값으로 넘겨서 **AI 추정 호출 없이** 정확한 영양정보로 등록할 수 있게 함. 세 사진 인식 API(영수증/주문내역/실물 상품) 모두 이 매칭을 공통으로 탄다.
- `ErrorMessage`에 범용 `IMAGE_REQUIRED`/`IMAGE_RECOGNITION_FAILED` 추가(주문내역/실물 상품 API용 — 기존 `RECEIPT_IMAGE_REQUIRED`/`RECEIPT_SCAN_FAILED`는 문구가 "영수증"에 특화돼 있어서 그대로 재사용하지 않음. `RECEIPT_IMAGE_TOO_LARGE`/`UNSUPPORTED_IMAGE_TYPE`은 문구가 이미 범용적이라 그대로 재사용).
- 프론트(cookgenieWeb): `ReceiptScanModal`을 `mode`(`'receipt'`|`'orderHistory'`|`'product'`) prop으로 일반화해서 세 버튼을 모두 실제 스캔 모달로 연결함. `matchedProcessedFood`/`matchedDish`가 있는 항목은 리뷰 화면에 🏛️ 배지를 보여주고, 등록 시 그 영양정보를 AI 추정 없이 직접 입력값으로 그대로 씀.

### 버그: 한글 인식률이 낮음(잘리거나 흐린 글자를 지어냄) — 비전 전용 모델 분리로 수정

실제 사용해보니 "생연어"를 "생영어"로, 잘려서 안 보이는 뒷부분("횟감용 (냉장)")을 전혀 다른 글자("흰갈충 (냉")로 지어내는 등 한글 인식 오류가 눈에 띔. 원인은 비용 절감을 위해 앱 전체(영양정보 추정/레시피 생성/영수증·주문내역·실물 상품 인식)가 전부 `anthropic.model=claude-haiku-4-5-20251001` 하나만 쓰고 있었던 것 — Haiku는 작은 글씨의 정밀한 한글 OCR에는 약함.

- `application.yml`에 `anthropic.vision-model: claude-sonnet-5`를 새로 추가하고, **비전을 쓰는 두 클라이언트(`ClaudeReceiptClient`, `ClaudeProductClient`)만** 이 모델로 바꿈 — 영양정보 추정/레시피 생성처럼 호출 빈도가 높고 정밀한 OCR이 필요 없는 나머지는 그대로 Haiku를 써서 비용 영향을 최소화함. 시크릿이 아니라 그냥 하드코딩된 설정값이라 배포 시 별도 환경변수 추가 불필요.
- 세 인식 프롬프트(영수증/주문내역/실물 상품) 전부에 "글자가 작거나 흐리거나 잘려서 확실히 안 보이면 절대로 비슷한 글자를 지어내지 말고, 분명하게 읽히는 부분까지만 적거나 그 항목을 제외해달라"는 지시를 추가함 — 모델이 불확실한 글자를 그럴듯하게 복원해버리는(confabulation) 패턴을 직접 겨냥한 지시.
- **배포 후 실제 테스트에서 발견된 버그(추가 수정함)**: 모델 교체 후에도 주문내역 인식에서 이름에 중량이 그대로 섞여 나옴(예: "무순, 60g") — 원인은 `scanOrderHistory()` 프롬프트 자체가 "상품명에 용량/옵션이 같이 적혀 있으면 그대로 이름에 포함해도 된다"고 명시적으로 허용하고 있었던 것(직접 지어낸 지시였음). 이름에 중량이 붙어있으면 (1) 정부 공식 데이터 이름 매칭이 실패하고(공식 데이터의 foodNm은 순수 상품명이라 "60g" 접미사가 없음), (2) 냉장고 등록 수량도 항상 기본값 1개로 저장되는 두 가지 문제로 이어짐. 세 인식 프롬프트(영수증/주문내역/실물 상품) 전부에 "중량/용량은 이름에 절대 포함하지 말고 quantityText/quantityValue/unit 필드로만 분리해달라"는 지시를 추가함.
- **연쇄 개선 — 공식 데이터 매칭 실패 시에도 AI 추정으로 폴백**: 위 문제와 별개로, 애초에 "무순"처럼 공식 데이터(가공식품/음식)에 없는 순수 채소·재료는 매칭이 안 돼서 `matchedProcessedFood`/`matchedDish`가 둘 다 null로 남고, 그러면 등록 시 영양정보가 아예 비어버려서 사용자가 "영양정보가 없다"고 느끼는 문제가 있었음. 사진 인식은 사용자가 몇 개만 골라서 등록하는 흐름이라(전체 재료 목록을 미리 추정하는 것과는 성격이 다름) 이 단계에서는 AI 추정 비용이 감당할 만하다고 판단해서, 프론트(`ReceiptScanModal.handleConfirm`)가 공식 데이터 매칭이 없는 항목은 `autoEstimateNutrition=true`로 등록하도록 바꿈 — 이제 매칭 3단계(재료 마스터 → 가공식품 → 음식) 전부 실패해도 Claude가 이름만으로 영양정보를 추정해서 등록됨.
- **아직 실제 재검증 못 함** — 다음에 같은 종류의 사진으로 다시 테스트해서 개선됐는지 확인 필요.

## 식단 기록(MealLog) API + 목표 영양정보(NutritionGoal) API

프론트에서 "오늘 하루 섭취량 상세 화면(삼성헬스 스타일)"과 "달력에서 날짜별 식사 요약 보기(캘린더 스타일)" 두 가지를 다 만들고 싶다는 요청으로 시작. `MealLog`/`MealLogItem`/`NutritionGoal` 엔티티와 리포지토리는 예전부터 있었지만 API가 없어서 미사용 상태였음 — 이번에 그 위에 서비스/컨트롤러 계층만 새로 얹음. **이번 작업은 백엔드까지만** — 프론트(두 화면) 구현은 아직 안 함, 다음 세션에서 이어가면 됨.

- `MealType` enum을 기존 5개(`BREAKFAST`/`LUNCH`/`DINNER`/`SNACK`/`LATE_NIGHT`)에서 화면 목업(삼성헬스)에 맞춰 6개(`BREAKFAST`/`LUNCH`/`DINNER`/`MORNING_SNACK`/`AFTERNOON_SNACK`/`EVENING_SNACK`)로 교체함. 이 테이블이 API가 없어서 실제 데이터가 전혀 없었기 때문에(신규 기능이라 마이그레이션 이슈 없음) 값만 바로 바꿈.
- `POST /meal-logs` — 식단 기록 추가. `logType=RECIPE`면 `recipeId`(+`servings`, 기본 1)로 기록하고 `Recipe`의 1인분 영양정보 × servings로 자동 계산. `logType=FREEFORM`이면 `items`(각 `ingredientId`+`quantity`+`unit`)로 기록하고, `FridgeItemResponse`와 동일한 방식(재료의 `NutritionInfo` 기준량 대비 비율 계산, 단위가 일치할 때만)으로 항목별 탄단지를 계산해 `MealLogItem`에 저장 + 합계를 `MealLog`에 저장. `FridgeItem`과 동일하게 **재료는 `ingredientId`로만 받음** — 이름으로 새 재료를 즉석 등록하지 않고, 프론트가 미리 `POST /ingredients`로 등록/재사용한 뒤 그 id를 넘기는 기존 설계를 그대로 따름.
- `GET /meal-logs?date=` — 하루 상세 조회. 아침/점심/저녁/오전간식/오후간식/저녁간식 6개 슬롯을 **항상 전부 포함**해서 내려주고(기록 없는 슬롯은 빈 배열), 슬롯별/하루 전체 합계와 그날 기준 적용 중인 목표(`NutritionGoal`) 대비 값을 함께 내려줌(목표 미설정이면 target* 필드는 전부 null).
- `GET /meal-logs/calendar?startDate=&endDate=` — 달력 범위 요약. 기록이 있는 날짜만 포함해서 날짜별 총 칼로리 + 식사별 대표 라벨(레시피면 제목, 직접입력이면 "재료명 외 N개")을 내려줌.
- `DELETE /meal-logs/{id}` — 본인 기록만 삭제 가능.
- `POST /nutrition-goals` — 목표 등록(칼로리/탄단지 + 체중/키/활동량, `effectiveDate` 생략 시 오늘부터 적용). 기존 값을 덮어쓰지 않고 새 row로 쌓이는 이력 구조(`NutritionGoalRepository.findFirstByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc`로 "그 날짜 기준 가장 최근 목표"를 조회) — 그래서 과거 날짜를 조회해도 그 시점에 실제 적용 중이던 목표가 나옴.
- `GET /nutrition-goals/current?date=` — 목표 조회(date 생략 시 오늘). 설정된 목표가 없으면 data가 null(에러 아님).
- `ErrorMessage`에 `MEAL_LOG_NOT_FOUND`/`MEAL_LOG_RECIPE_REQUIRED`/`MEAL_LOG_ITEMS_REQUIRED` 추가.

## 설정 화면(닉네임 변경) + 냉장고 멤버 관리(목록/강퇴/탈퇴)

프론트에 설정 화면이 아예 없었고(로그아웃만 TopBar에 있었음), 냉장고 공유 화면(`SharePage`)도 초대코드 발급/참여까지만 있고 "참여 중인 멤버 목록 보기·내보내기 기능은 아직 준비 중"이라는 안내문만 있던 상태였음. 사용자가 다른 앱 설정 화면 스크린샷을 참고로 보여주며 두 가지를 요청함: (1) 닉네임 변경 가능한 설정 화면 + 알림 기능은 나중에 네이티브 앱을 만들 때를 대비해 프론트에서 더미로만 (2) 냉장고 멤버 목록 조회 + 그룹장의 강퇴 + 멤버 본인의 탈퇴.

- `PATCH /auth/nickname`(`NicknameUpdateRequest{nickname}`) 추가 — 닉네임은 유니크 제약이 없어서(이메일/아이디와 다름) 중복 검사 없이 그냥 바꿔줌. `User.updateNickname()` mutator 추가.
- `GET /fridges/{fridgeId}/members` — 멤버 목록(`FridgeMemberResponse{userId, nickname, role, joinedAt}`), 소유자 먼저·참여일 순 정렬. 멤버라면 소유자/일반 멤버 누구나 조회 가능.
- `DELETE /fridges/{fridgeId}/members/{userId}` — 멤버 강퇴, OWNER만 가능하고 자기 자신은 강퇴 못 함(`CANNOT_KICK_SELF`).
- `DELETE /fridges/{fridgeId}/leave` — 본인 탈퇴. **OWNER는 탈퇴 불가**(`CANNOT_LEAVE_AS_OWNER`) — 소유권 이전 기능이 없어서 먼저 냉장고를 삭제하거나(2.4) 다른 사람에게 소유권을 넘기는 기능이 생길 때까지는 이 제약을 유지. `ErrorMessage`에 `FRIDGE_MEMBER_NOT_FOUND`/`CANNOT_KICK_SELF`/`CANNOT_LEAVE_AS_OWNER` 추가.
- 프론트: `Sidebar`에 "설정"(`/settings`) 네비 항목 추가, 새 `SettingsPage`(닉네임 변경 폼 + 알림 토글은 `localStorage`에만 저장하는 순수 프론트 더미, 서버 호출 없음). `SharePage`에 멤버 목록 카드 추가(본인이 OWNER면 각 멤버 옆에 강퇴 버튼, 본인이 MEMBER면 "탈퇴하기" 버튼 — 자기 행에는 강퇴 버튼 대신 "나" 표시).

## 다음 할 일 후보 (우선순위 순 아님, 상황 보고 정하기)

- `FridgeItem`/`Recipe`/장보기/**식단 기록** API들의 냉장고·본인 권한 검증 강화 (지금은 냉장고 존재 여부 또는 최소한의 소유자 확인 정도만)
- 프론트엔드의 "영양정보 동기화" 버튼 — 이제 없는 엔드포인트(`/ingredients/sync-raw-materials`)를 호출하고 있어서 프론트에서 제거 필요
- 기존에 영양정보 없이 등록된 재료들(예: 계란/목살/양파/소금 등)을 일괄로 재추정하는 백필(backfill) 기능 (요청은 있었으나 미구현)
- 구글 소셜 로그인(카카오는 완료) / 이메일 인증 / 비밀번호 재설정 / 로그아웃 시 JWT 즉시 무효화
- **식단 캘린더 프론트 구현** (cookgenieWeb) — 위 MealLog API를 갖고 "오늘 상세" 화면(진행률 링 + 탄단지 바 + 6개 슬롯 리스트) + "달력" 화면(월별 그리드, 날짜별 식사 요약 배지) 두 가지를 만들어야 함
- **사진으로 재료 등록 2/3단계 프론트 연결** — 백엔드는 완료(`POST /fridges/{fridgeId}/receipts/scan-order-history`, `POST /fridges/{fridgeId}/products/scan`), 프론트(cookgenieWeb)의 "주문 내역 인식"/"재료 인식" 버튼이 아직 `handleDummyRecognition()`으로 알럿만 띄우는 상태라 `ReceiptScanModal`을 일반화하거나 sibling 컴포넌트로 연결해야 함
