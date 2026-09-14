# CookGenie API 명세

## 공통 사항

### 응답 포맷 (`GlobalResponse<T>`)

인증(`/auth/**`)과 냉장고(`/fridges/**`) API는 모두 아래 포맷으로 감싸서 응답합니다.

```json
{
  "success": true,
  "data": { ... },
  "message": null
}
```

- 실패 시 `success: false`, `data: null`, `message`에 에러 메시지가 담깁니다.
- 예외: `DELETE` 응답은 `204 No Content`(바디 없음)이고, 인증 필터 자체에서 막히는 401/403은 `GlobalResponse`가 아닌 자체 포맷(`{"statusCode": 401, "message": "..."}`)입니다.

### 인증 헤더

로그인/회원가입 이후 발급받은 `accessToken`을 아래 헤더로 실어 보내야 하는 API(`/auth/profile`, `/fridges/**`)에 사용합니다.

```
Authorization: Bearer {accessToken}
```

### 에러 코드 (`ErrorMessage`)

| 상황 | HTTP Status | message |
|---|---|---|
| 비밀번호 불일치 | 401 | 비밀번호가 일치하지 않습니다. |
| 유효하지 않은 리프레시 토큰 | 401 | 유효하지 않은 리프레시 토큰입니다. |
| 리프레시 토큰 만료 | 401 | 토큰이 만료되었습니다. |
| 사용자 없음 | 404 | 사용자를 찾을 수 없습니다. |
| 냉장고 없음 | 404 | 냉장고를 찾을 수 없습니다. |
| 냉장고 재료 없음 | 404 | 냉장고 재료를 찾을 수 없습니다. |
| 식재료 없음 | 404 | 식재료를 찾을 수 없습니다. |
| 이메일 중복 | 409 | 이미 존재하는 이메일입니다. |
| 아이디 중복 | 409 | 이미 존재하는 아이디입니다. |
| 탈퇴한 계정으로 로그인 시도 | 409 | 탈퇴한 계정입니다. |
| 소셜 로그인 계정으로 가입 시도 | 422 | 소셜 로그인으로 가입된 계정입니다. |
| 냉장고 접근 권한 없음(멤버 아님) | 403 | 접근 권한이 없습니다. |
| 유튜브 영상 없음 | 404 | 유튜브 영상을 찾을 수 없습니다. |

---

## 1. Auth API (`/auth`)

### 1.1 회원가입 — `POST /auth/sign`

인증 불필요.

**Request Body** (`SignupRequest`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| loginId | String | O | 로그인 아이디 |
| password | String | O | 비밀번호 (평문 전송 → 서버에서 BCrypt 해싱) |
| email | String | O | 이메일 (형식 검증) |
| nickname | String | O | 닉네임 |
| profileImageUrl | String | X | 프로필 이미지 URL |

**Response** `200 OK` — `GlobalResponse<TokenResponse>`

```json
{
  "success": true,
  "data": { "accessToken": "...", "refreshToken": "..." },
  "message": null
}
```

**에러**: 이메일 중복(409), 아이디 중복(409), 이미 소셜 계정으로 가입된 이메일(422)

---

### 1.2 로그인 — `POST /auth/login`

인증 불필요.

**Request Body** (`LoginRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| loginId | String | O |
| password | String | O |

**Response** `200 OK` — `GlobalResponse<TokenResponse>` (형식은 회원가입과 동일)

**에러**: 사용자 없음(404), 비밀번호 불일치(401)

---

### 1.3 토큰 재발급 — `POST /auth/reissue`

인증 불필요 (refreshToken 자체가 자격증명).

**Request Body** (`RefreshTokenReissueRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| refreshToken | String | O |

**Response** `200 OK` — `GlobalResponse<TokenResponse>` (새 accessToken + refreshToken, 토큰 로테이션)

**에러**: 리프레시 토큰 만료(401), 유효하지 않은 리프레시 토큰(401), 사용자 없음(404)

---

### 1.4 내 프로필 조회 — `GET /auth/profile`

**인증 필요** (`Authorization: Bearer {accessToken}`)

**Response** `200 OK` — `GlobalResponse<UserInfoResponse>`

```json
{
  "success": true,
  "data": {
    "id": 1,
    "loginId": "tester1",
    "email": "tester1@example.com",
    "nickname": "tester",
    "profileImageUrl": null,
    "createdAt": "2026-08-23T15:08:19.28262",
    "updatedAt": "2026-08-23T15:08:19.28262"
  },
  "message": null
}
```

---

### 1.5 로그아웃 — `POST /auth/logout`

**인증 필요**

서버에 저장된 refresh token을 삭제합니다. **주의**: access token은 stateless JWT라 서버에서 즉시 무효화할 수 없고, 발급 시 설정된 만료시간(7일)까지는 계속 유효합니다. 완전한 즉시 무효화가 필요하면 추후 블랙리스트 방식을 별도로 도입해야 합니다.

**Response** `200 OK` — `GlobalResponse<Void>` (`data: null`)

---

### 1.6 회원 탈퇴 — `DELETE /auth/withdraw`

**인증 필요**

비밀번호 재확인 후 계정을 탈퇴 처리합니다. 하드 삭제가 아니라 **soft delete**입니다 (`User.status`를 `WITHDRAWN`으로 변경 + `password`를 null로 초기화 + 저장된 refresh token 삭제). `email`/`loginId`는 그대로 남아있어 동일한 값으로 재가입은 불가능합니다. 소유했던 냉장고, 식단 기록 등 다른 데이터는 삭제되지 않고 그대로 남습니다.

**Request Body** (`WithdrawRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| password | String | O |

**Response** `200 OK` — `GlobalResponse<Void>` (`data: null`)

**에러**: 비밀번호 불일치(401)

---

## 2. 냉장고 API (`/fridges`)

**전부 인증 필요.**

### 2.1 냉장고 생성 — `POST /fridges`

인증된 사용자를 소유자(`owner`)로 하는 냉장고를 만들고, 동시에 `FridgeMember`에 `OWNER` 역할로 등록합니다.

**Request Body** (`FridgeCreateRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| name | String | O |

**Response** `200 OK` — `GlobalResponse<FridgeResponse>`

---

### 2.2 내 냉장고 목록 — `GET /fridges`

내가 멤버(소유자 포함)로 속한 모든 냉장고를 반환합니다.

**Response** `200 OK` — `GlobalResponse<List<FridgeResponse>>`

---

### 2.3 냉장고 단건 조회 — `GET /fridges/{fridgeId}`

**Response** `200 OK` — `GlobalResponse<FridgeResponse>`

**에러**: 접근 권한 없음(403) — 요청자가 해당 냉장고의 `FridgeMember`가 아닌 경우

---

### 2.4 냉장고 삭제 — `DELETE /fridges/{fridgeId}`

**OWNER만 가능합니다.** 삭제 시 그 냉장고에 속한 `FridgeItem`, `FridgeMember`를 먼저 정리한 뒤 냉장고 자체를 삭제합니다.

**Response** `204 No Content` (바디 없음)

**에러**: 접근 권한 없음(403) — 멤버가 아니거나, 멤버여도 `OWNER`가 아닌 경우

---

### 공통 DTO — `FridgeResponse`

| 필드 | 타입 |
|---|---|
| id | Long |
| name | String |
| ownerId | Long |
| ownerNickname | String |
| myRole | String (`OWNER` \| `MEMBER`) — 요청자 본인의 역할 |
| createdAt | LocalDateTime |
| updatedAt | LocalDateTime |

---

## 3. 냉장고 재료 API (`/fridges/{fridgeId}/items`)

**전부 인증 필요.** 다만 이 엔드포인트들은 냉장고의 존재 여부만 확인하고, 요청자가 그 냉장고의 `FridgeMember`인지는 아직 검증하지 않습니다 (2번 냉장고 API와 달리 권한 체크 미적용 — 다른 사용자의 `fridgeId`를 알면 재료를 조작할 수 있는 상태이니, 다음 작업에서 반드시 보완이 필요합니다).

### 공통 DTO

**`FridgeItemResponse`**

| 필드 | 타입 |
|---|---|
| id | Long |
| fridgeId | Long |
| ingredientId | Long |
| ingredientName | String |
| categoryName | String \| null |
| quantity | BigDecimal |
| unit | String |
| storageLocation | String (`REFRIGERATED` \| `FROZEN` \| `ROOM_TEMP`) |
| purchasedAt | LocalDate |
| expiryDate | LocalDate \| null |
| memo | String \| null |
| calories | Integer \| null |
| carbohydrateG | BigDecimal \| null |
| proteinG | BigDecimal \| null |
| fatG | BigDecimal \| null |
| referenceAmount | Integer \| null |
| referenceUnit | String \| null |
| referenceCalories | Integer \| null |
| referenceCarbohydrateG | BigDecimal \| null |
| referenceProteinG | BigDecimal \| null |
| referenceFatG | BigDecimal \| null |
| createdAt | LocalDateTime |
| updatedAt | LocalDateTime |

`calories`/`carbohydrateG`/`proteinG`/`fatG`는 식재료의 `NutritionInfo`(100g/ml 기준)를 `quantity`만큼 환산한 값입니다. **`unit`이 그 재료의 영양정보 기준 단위(`referenceUnit`, 보통 `g`)와 정확히 일치할 때만 계산**되고, 단위가 다르면(예: `개`, `큰술`) 억지로 환산하지 않고 전부 `null`로 내려갑니다.

`reference*` 필드는 환산 없이 그 재료의 "기준량(`referenceAmount` `referenceUnit`, 보통 100g)당" 원래 값을 그대로 보여줍니다 — 예를 들어 화면에 "100g당 탄수화물 6.5g"처럼 참고용으로 같이 띄울 때 씁니다. 영양정보 자체가 없는 재료(수동 등록 등)면 전부 `null`입니다.

---

### 3.1 재료 추가 — `POST /fridges/{fridgeId}/items`

**Request Body** (`FridgeItemCreateRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| ingredientId | Long | O |
| quantity | BigDecimal | O |
| unit | String | O |
| storageLocation | String (enum) | O |
| purchasedAt | LocalDate | O |
| expiryDate | LocalDate | X |
| memo | String | X |

**Response** `200 OK` — `GlobalResponse<FridgeItemResponse>`

**에러**: 냉장고 없음(404), 식재료 없음(404)

---

### 3.2 재료 목록 조회 — `GET /fridges/{fridgeId}/items`

**Response** `200 OK` — `GlobalResponse<List<FridgeItemResponse>>`

**에러**: 냉장고 없음(404)

---

### 3.3 재료 단건 조회 — `GET /fridges/{fridgeId}/items/{itemId}`

**Response** `200 OK` — `GlobalResponse<FridgeItemResponse>`

**에러**: 냉장고 재료 없음(404) — `itemId`가 존재하지 않거나 `fridgeId`에 속하지 않는 경우

---

### 3.4 재료 수정 — `PUT /fridges/{fridgeId}/items/{itemId}`

**Request Body** (`FridgeItemUpdateRequest`) — `ingredientId`는 변경 불가(교체하려면 삭제 후 재등록)

| 필드 | 타입 | 필수 |
|---|---|---|
| quantity | BigDecimal | O |
| unit | String | O |
| storageLocation | String (enum) | O |
| purchasedAt | LocalDate | O |
| expiryDate | LocalDate | X |
| memo | String | X |

**Response** `200 OK` — `GlobalResponse<FridgeItemResponse>`

**에러**: 냉장고 재료 없음(404)

---

### 3.5 재료 삭제 — `DELETE /fridges/{fridgeId}/items/{itemId}`

**Response** `204 No Content` (바디 없음)

**에러**: 냉장고 재료 없음(404)

---

## 4. 식재료 API (`/ingredients`)

**전부 인증 필요.**

### 4.1 식재료 검색 — `GET /ingredients?keyword=`

이름에 `keyword`가 포함된 식재료를 검색합니다. `keyword`가 없으면 전체 목록을 반환합니다. 냉장고에 재료를 추가하기 전, `ingredientId`를 얻기 위해 사용합니다.

**Response** `200 OK` — `GlobalResponse<List<IngredientResponse>>`

### 4.2 카테고리 목록 조회 — `GET /ingredients/categories`

**Response** `200 OK` — `GlobalResponse<List<CategoryResponse>>`

### 4.3 식재료 등록 — `POST /ingredients`

검색 결과에 없는 식재료를 등록합니다. **동작 방식**:

1. 정확히 같은 이름의 식재료가 이미 있으면 새로 만들지 않고 **그대로 재사용**합니다(중복 방지) — 다른 사용자가 이미 "양파"를 등록해뒀다면 그 영양정보를 그대로 돌려받습니다.
2. 완전히 새 이름이면 **Claude(Anthropic API)에게 100g 기준 평균 영양정보를 추정**시켜서 `NutritionInfo`와 함께 저장합니다. 이때 `dataSource=LLM_ESTIMATED`, `isVerified=false`로 생성됩니다.
3. Claude 호출이 실패하거나(계정 크레딧 부족 등) 실제 식재료가 아니라고 판단되면, 영양정보 없이 `dataSource=USER_INPUT`으로 등록됩니다(기존과 동일한 폴백).

**Request Body** (`IngredientCreateRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| name | String | O |
| categoryName | String | O (없는 이름이면 카테고리 자동 생성) |
| defaultUnit | String | X (비워두면 Claude가 추정한 단위를 씀) |

**Response** `200 OK` — `GlobalResponse<IngredientResponse>` (LLM 호출이 걸리면 응답까지 1~2초 정도 걸릴 수 있음)

### 4.4 식재료 수정 — `PUT /ingredients/{id}`

**Request Body** (`IngredientUpdateRequest`) — 필드는 4.3과 동일

**Response** `200 OK` — `GlobalResponse<IngredientResponse>` · **에러**: 식재료 없음(404)

### 4.5 식재료 삭제 — `DELETE /ingredients/{id}`

**Response** `204 No Content` · **에러**: 식재료 없음(404), 이미 어떤 냉장고에 등록되어 삭제 불가(409)

### 공통 DTO

**`IngredientResponse`**

| 필드 | 타입 |
|---|---|
| id | Long |
| name | String |
| categoryId | Long \| null |
| categoryName | String \| null |
| ingredientType | String (`RAW` \| `PROCESSED`) |
| defaultUnit | String \| null |
| dataSource | String (`OFFICIAL_DB` \| `OCR` \| `LLM_ESTIMATED` \| `USER_INPUT`) |
| isVerified | Boolean |
| referenceAmount | Integer \| null |
| referenceUnit | String \| null |
| referenceCalories | Integer \| null |
| referenceCarbohydrateG | BigDecimal \| null |
| referenceProteinG | BigDecimal \| null |
| referenceFatG | BigDecimal \| null |

`reference*`는 그 재료의 기준량(보통 100g)당 영양정보입니다. 냉장고에 추가하기 전 검색 단계에서도 "100g당 탄단지"를 미리 볼 수 있도록 포함했습니다. 영양정보가 없는 재료(수동 등록 등)는 전부 `null`입니다.

**`CategoryResponse`**: `id`, `name`, `iconUrl`

---

## 5. 레시피 API

**전부 인증 필요.**

### 5.1 AI 레시피 생성 — `POST /fridges/{fridgeId}/recipes/generate`

냉장고에 있는 재료 이름들을 Claude에게 전달해서 만들 수 있는 레시피 하나를 생성하고 저장합니다(`recipeType=AI`). 재료는 냉장고에 있는 것만 쓰라는 게 아니라, 소금/식용유/후추/다진마늘 같은 흔한 기본 양념은 Claude가 알아서 추가할 수 있습니다 — 그렇게 추가된 재료는 `RecipeIngredient.ingredientId`가 `null`로 텍스트만 표시됩니다.

**Request Body** (`AiRecipeGenerateRequest`, 바디 자체를 생략해도 됨)

| 필드 | 타입 | 필수 |
|---|---|---|
| note | String | X — 예: "매콤하게", "국물 요리로" 같은 추가 요청사항 |

**Response** `200 OK` — `GlobalResponse<RecipeResponse>` (Claude 호출 때문에 응답까지 수 초 걸릴 수 있음)

**에러**: 냉장고 없음(404), 냉장고에 재료 없음(400), 레시피 생성 실패(502 — Claude 호출 실패/크레딧 부족 등)

---

### 5.2 재료 기반 레시피 추천 — `GET /fridges/{fridgeId}/recipes/recommendations?limit=`

냉장고 재료와 겹치는 재료가 많은 레시피 순으로 정렬해서 추천합니다(겹치는 비율 우선, 동률이면 겹치는 개수 우선). **하나도 안 겹치는 레시피는 목록에서 빠집니다.** `limit` 생략 시 기본 20개.

**Response** `200 OK` — `GlobalResponse<List<RecipeSummaryResponse>>` (각 항목에 `matchedIngredientCount`/`totalIngredientCount` 포함)

**에러**: 냉장고 없음(404)

---

### 5.3 유튜브 레시피 검색 — `GET /fridges/{fridgeId}/recipes/youtube/search?keyword=&limit=`

YouTube Data API v3로 요리 영상을 검색합니다. **결과는 저장되지 않는 미리보기**입니다 — 실제로 레시피에 반영하려면 5.4(가져오기)를 별도로 호출해야 합니다.

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword | String | X | 검색어. 생략하면 냉장고 재료 이름(최대 3개)으로 자동 구성 |
| limit | Integer | X | 최대 개수 (기본 10) |

**Response** `200 OK` — `GlobalResponse<List<YoutubeVideoSummaryResponse>>`

**에러**: 냉장고 없음(404), 냉장고에 재료 없음(400) — `keyword` 없이 재료도 없는 냉장고로 검색한 경우

**`YoutubeVideoSummaryResponse`**: `videoId`, `title`, `description`, `channelTitle`, `thumbnailUrl`, `publishedAt`, `videoUrl`

---

### 5.4 유튜브 레시피 가져오기 — `POST /recipes/youtube/import`

5.3에서 찾은 영상의 `videoId`로 영상 상세(제목/설명/채널명)를 조회하고, 그 내용을 Claude에게 전달해 재료/조리법/1인분 영양정보를 추출한 뒤 `recipeType=YOUTUBE`로 저장합니다. 설명란에 재료/조리법이 명확하지 않으면 Claude가 제목과 일반적인 요리 지식으로 추정합니다. **이미 가져온 영상(`sourceUrl` 기준)이면 다시 호출하지 않고 기존 레시피를 그대로 반환**합니다.

**Request Body**

| 필드 | 타입 | 필수 |
|---|---|---|
| videoId | String | O |

**Response** `200 OK` — `GlobalResponse<RecipeResponse>` (Claude 호출 때문에 응답까지 수 초 걸릴 수 있음). 저장된 레시피는 `sourceUrl`(영상 URL)과 `authorNickname`(채널명)이 채워집니다.

**에러**: 유튜브 영상 없음(404) — `videoId`가 잘못되었거나 조회에 실패한 경우, 레시피 생성 실패(502 — Claude 호출 실패/크레딧 부족 등)

---

### 5.5 레시피 목록 조회 — `GET /recipes`

전체 레시피를 최신 등록순으로 반환합니다. `matchedIngredientCount`/`totalIngredientCount`는 냉장고 문맥이 아니라서 항상 `null`입니다.

**Response** `200 OK` — `GlobalResponse<List<RecipeSummaryResponse>>`

### 5.6 레시피 상세 조회 — `GET /recipes/{id}`

**Response** `200 OK` — `GlobalResponse<RecipeResponse>` · **에러**: 레시피 없음(404)

### 5.7 레시피 삭제 — `DELETE /recipes/{id}`

연결된 재료(`RecipeIngredient`)와 태그(`RecipeTag`)도 함께 삭제합니다.

**Response** `204 No Content` · **에러**: 레시피 없음(404)

---

### 공통 DTO

**`RecipeResponse`** (상세)

| 필드 | 타입 |
|---|---|
| id | Long |
| title | String |
| recipeType | String (`AI` \| `YOUTUBE` \| `USER`) |
| cookingType | String \| null |
| instructions | String[] — 조리 순서, 단계별 문장 배열 |
| sourceUrl | String \| null |
| authorNickname | String \| null |
| servingSize | Integer \| null |
| caloriesPerServing | Integer \| null |
| carbohydrateG / proteinG / fatG | BigDecimal \| null — 1인분 기준 |
| viewCount / likeCount / saveCount | Integer |
| tags | String[] |
| ingredients | `RecipeIngredientResponse[]` |
| createdAt / updatedAt | LocalDateTime |

**`RecipeIngredientResponse`**: `id`, `ingredientId`(매칭 안 되면 null), `ingredientNameText`, `quantityText`, `quantityValue`, `unit`, `matched`(boolean — ingredientId 유무와 동일)

**`RecipeSummaryResponse`** (목록/추천용, `RecipeResponse`에서 `instructions`/`sourceUrl`/`authorNickname`/`tags`/`ingredients` 제외 + `matchedIngredientCount`/`totalIngredientCount`(Integer, null 가능) 추가

---

## 아직 구현되지 않은 것

- 소셜 로그인(카카오/네이버/구글/애플), 이메일 인증, 비밀번호 재설정
- 냉장고 멤버 초대 API (`FridgeMember`를 `OWNER`가 만들 때 자동 등록만 되고, 다른 사용자를 멤버로 추가하는 API는 없음)
- **`FridgeItem`/레시피 API들의 멤버 권한 검증** — 현재 냉장고 존재 여부만 확인하고 요청자가 해당 냉장고 멤버인지는 확인하지 않음 (2번 냉장고 API는 이미 `FridgeMember` 기반 검증 적용됨)
- 로그아웃 시 access token 즉시 무효화 (현재는 만료시간까지 유효한 stateless JWT 한계 그대로)
- 식단 기록(MealLog) 관련 API
- 레시피 좋아요/저장(`likeCount`/`saveCount` 증가), 조회수 집계
