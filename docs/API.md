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
| 카테고리 없음 | 404 | 카테고리를 찾을 수 없습니다. |
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

### 1.2 게스트로 시작 — `POST /auth/guest`

인증 불필요. Request Body 없음.

회원가입 없이 바로 쓸 수 있는 임시 계정을 만들고 access/refresh 토큰을 즉시 발급합니다. 냉장고 생성/재료 등록/AI 레시피 생성 등 일반 회원과 완전히 동일하게 이용할 수 있습니다.

**단, 3일 안에 [1.3 게스트 → 정식 회원 전환](#13-게스트--정식-회원-전환--post-authguestupgrade)을 하지 않으면 계정과 그 안의 모든 데이터(냉장고/재료 등)가 서버에서 자동으로 영구 삭제됩니다.** 프론트에서는 이 응답으로 받은 토큰을 저장해두고, `GET /auth/profile`의 `guestExpiresAt`을 이용해 "n일 후 데이터가 삭제됩니다" 같은 안내 배너를 노출해주세요.

**Response** `200 OK` — `GlobalResponse<TokenResponse>` (형식은 회원가입과 동일)

---

### 1.3 게스트 → 정식 회원 전환 — `POST /auth/guest/upgrade`

**인증 필요** (게스트 계정의 accessToken)

Request Body는 회원가입(`SignupRequest`)과 동일합니다. 같은 유저 id를 그대로 승격시키는 방식이라 **게스트로 등록해둔 냉장고/재료 데이터가 이관 과정 없이 그대로 유지**됩니다. 성공 시 새 access/refresh 토큰을 발급하므로, 기존에 들고 있던 게스트 토큰은 폐기하고 응답으로 받은 새 토큰으로 교체해야 합니다.

**Response** `200 OK` — `GlobalResponse<TokenResponse>`

**에러**: 게스트 계정이 아님(400), 이메일 중복(409), 아이디 중복(409)

---

### 1.4 로그인 — `POST /auth/login`

인증 불필요.

**Request Body** (`LoginRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| loginId | String | O |
| password | String | O |

**Response** `200 OK` — `GlobalResponse<TokenResponse>` (형식은 회원가입과 동일)

**에러**: 사용자 없음(404), 비밀번호 불일치(401)

---

### 1.5 토큰 재발급 — `POST /auth/reissue`

인증 불필요 (refreshToken 자체가 자격증명).

**Request Body** (`RefreshTokenReissueRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| refreshToken | String | O |

**Response** `200 OK` — `GlobalResponse<TokenResponse>` (새 accessToken + refreshToken, 토큰 로테이션)

**에러**: 리프레시 토큰 만료(401), 유효하지 않은 리프레시 토큰(401), 사용자 없음(404)

---

### 1.6 내 프로필 조회 — `GET /auth/profile`

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
    "updatedAt": "2026-08-23T15:08:19.28262",
    "guest": false,
    "guestExpiresAt": null
  },
  "message": null
}
```

`guest`가 `true`이면 게스트 계정이며, `guestExpiresAt`은 이 계정과 데이터가 자동 삭제되는 시각(생성 후 3일)입니다. 정식 회원(`guest: false`)이면 `guestExpiresAt`은 항상 `null`입니다.

---

### 1.7 로그아웃 — `POST /auth/logout`

**인증 필요**

서버에 저장된 refresh token을 삭제합니다. **주의**: access token은 stateless JWT라 서버에서 즉시 무효화할 수 없고, 발급 시 설정된 만료시간(7일)까지는 계속 유효합니다. 완전한 즉시 무효화가 필요하면 추후 블랙리스트 방식을 별도로 도입해야 합니다.

**Response** `200 OK` — `GlobalResponse<Void>` (`data: null`)

---

### 1.8 회원 탈퇴 — `DELETE /auth/withdraw`

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

### 2.5 초대코드 발급 — `POST /fridges/{fridgeId}/invite-code`

**OWNER만 가능합니다.** 4자리 숫자 코드를 생성해서 **7일간** 유효하게 발급합니다. 다시 발급하면 이전 코드는 즉시 무효화되고(냉장고당 코드는 항상 하나만 활성 상태) 새 코드로 대체됩니다.

**Response** `200 OK` — `GlobalResponse<FridgeInviteCodeResponse>`

```json
{
  "success": true,
  "data": { "inviteCode": "4661", "expiryDate": "2026-09-23T00:44:58.984827" },
  "message": null
}
```

**에러**: 접근 권한 없음(403) — 멤버가 아니거나, 멤버여도 `OWNER`가 아닌 경우

---

### 2.6 초대코드로 냉장고 참여 — `POST /fridges/join`

다른 사람에게 전달받은 4자리 초대코드를 입력해서 그 냉장고에 `MEMBER`로 참여합니다. 어떤 냉장고인지 미리 알 필요 없이 코드만으로 참여됩니다.

**Request Body** (`FridgeJoinRequest`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| inviteCode | String | O | 4자리 숫자 문자열 (예: `"4661"`) |

**Response** `200 OK` — `GlobalResponse<FridgeResponse>` (`myRole: "MEMBER"`)

**에러**: 유효하지 않거나 만료된 코드(400), 이미 참여 중인 냉장고(409)

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

### 3.6 냉장고 재료 현황 통계 — `GET /fridges/{fridgeId}/statistics`

총 재료 수, 소비기한 임박/지남 개수, 카테고리·보관위치별 분포, 주의가 필요한 재료, 오래 방치된 재료, 최근 등록 활동 히트맵을 한 번에 조회합니다. "임박"은 오늘부터 3일 이내(오늘 포함) 만료, "지남"은 만료일이 오늘보다 이전인 경우입니다.

**Response** `200 OK` — `GlobalResponse<FridgeStatisticsResponse>`

**에러**: 냉장고 없음(404)

**`FridgeStatisticsResponse`**

| 필드 | 타입 | 설명 |
|---|---|---|
| totalItemCount | long | 전체 재료 개수 |
| expiringSoonCount | long | 소비기한이 3일 이내(오늘 포함)로 남은 재료 개수. 이미 지난 건 제외 |
| expiredCount | long | 소비기한이 지난 재료 개수 |
| categoryDistribution | `CategoryDistribution[]` | 카테고리별 개수(내림차순). 카테고리 없는 재료는 `categoryName="미분류"`로 묶임 |
| storageLocationDistribution | `StorageLocationDistribution[]` | 보관위치별 개수(내림차순) |
| expiryAttentionItems | `FridgeItemResponse[]` | 소비기한이 임박했거나 이미 지난 재료. 만료일이 가까운(또는 지난) 순으로 최대 5개 |
| longNeglectedItems | `FridgeItemResponse[]` | 구매일이 가장 오래된 재료 최대 5개("방치된 재료") |
| activityHeatmap | `DailyActivityCount[]` | 최근 150일간 날짜별 재료 등록 개수(`FridgeItem.createdAt` 기준). 등록 없는 날도 `count=0`으로 포함되어 항상 150개 |

**`CategoryDistribution`**: `categoryName`, `count`
**`StorageLocationDistribution`**: `storageLocation`(enum), `count`
**`DailyActivityCount`**: `date`(LocalDate), `count`

---

## 4. 식재료 API (`/ingredients`)

**전부 인증 필요.**

### 4.1 식재료 검색 — `GET /ingredients?keyword=`

이름에 `keyword`가 포함된 식재료를 검색합니다. `keyword`가 없으면 전체 목록을 반환합니다. 냉장고에 재료를 추가하기 전, `ingredientId`를 얻기 위해 사용합니다.

**Response** `200 OK` — `GlobalResponse<List<IngredientResponse>>`

### 4.2 카테고리 목록 조회 — `GET /ingredients/categories`

**Response** `200 OK` — `GlobalResponse<List<CategoryResponse>>`

### 4.3 카테고리별 추천 재료 조회 — `GET /ingredients/categories/{categoryId}/suggestions`

재료 추가 화면에서 카테고리를 고르면 바로 보여줄 수 있는, 자주 쓰는 재료 이름 목록입니다. **DB/AI 호출이 전혀 없는 정적 목록**이라 즉시 응답합니다. 사용자가 그중 하나를 고르면, 그 이름 그대로 4.4(등록)를 호출하면 됩니다 — 이미 같은 이름이 등록돼 있으면 즉시 재사용되고, 처음 등록되는 이름이면 그때 Claude가 영양정보를 추정합니다. 추천 목록의 이름을 항상 고정해두는 이유는, 여러 사용자가 같은 재료를 골라도 "돼지고기" vs "돼지 고기"처럼 표기가 달라 중복 등록/중복 추정되는 걸 막기 위함입니다.

**Response** `200 OK` — `GlobalResponse<List<IngredientSuggestionResponse>>`

**에러**: 카테고리 없음(404)

**`IngredientSuggestionResponse`**: `name`

### 4.4 식재료 등록 — `POST /ingredients`

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

### 4.5 식재료 수정 — `PUT /ingredients/{id}`

**Request Body** (`IngredientUpdateRequest`) — 필드는 4.4와 동일

**Response** `200 OK` — `GlobalResponse<IngredientResponse>` · **에러**: 식재료 없음(404)

### 4.6 식재료 삭제 — `DELETE /ingredients/{id}`

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

레시피 하나를 생성하고 저장합니다(`recipeType=AI`). `useFridgeIngredients`(기본 true)에 따라 두 가지 모드로 동작합니다:

- **`useFridgeIngredients=true`(기본값, 생략해도 이 동작)**: 냉장고에 있는 재료 이름들을 Claude에게 전달해서 그 재료로 만들 수 있는 레시피를 생성합니다. 재료는 냉장고에 있는 것만 쓰라는 게 아니라, 소금/식용유/후추/다진마늘 같은 흔한 기본 양념은 Claude가 알아서 추가할 수 있습니다 — 그렇게 추가된 재료는 `RecipeIngredient.ingredientId`가 `null`로 텍스트만 표시됩니다. **냉장고에 재료가 하나도 없으면 400 에러**가 납니다.
- **`useFridgeIngredients=false`**: 냉장고 재료를 완전히 무시하고, `note`에 적힌 요청사항에만 맞는 레시피를 자유롭게 생성합니다(냉장고에 재료가 없어도 됨). `note`도 비워두면 Claude가 아무 요리나 추천합니다. 지금 냉장고에 없는 재료로 새로운 레시피를 시도해보고 싶을 때 씁니다.

**Request Body** (`AiRecipeGenerateRequest`, 바디 자체를 생략해도 됨 — 이 경우 `useFridgeIngredients=true`로 동작)

| 필드 | 타입 | 필수 |
|---|---|---|
| note | String | X — 예: "매콤하게", "국물 요리로" 같은 추가 요청사항 |
| useFridgeIngredients | Boolean | X — 기본 `true`. `false`면 냉장고 재료 무시하고 note로만 자유 생성 |

**Response** `200 OK` — `GlobalResponse<RecipeResponse>` (Claude 호출 때문에 응답까지 수 초 걸릴 수 있음)

**에러**: 냉장고 없음(404), 냉장고에 재료 없음(400 — `useFridgeIngredients=true`일 때만), 레시피 생성 실패(502 — Claude 호출 실패/크레딧 부족 등)

이 API는 항상 이 fridgeId 기준으로 `ingredients[].inFridge`를 계산해서 내려줍니다(6번 장보기 기능과 연동 — 아래 참고).

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

### 5.6 레시피 상세 조회 — `GET /recipes/{id}?fridgeId=`

`fridgeId`를 함께 주면 그 냉장고 기준으로 `ingredients[].inFridge`를 계산해서 내려줍니다. 생략하면 `inFridge`는 전부 `null`입니다(어떤 냉장고 기준인지 모르므로).

**Response** `200 OK` — `GlobalResponse<RecipeResponse>` · **에러**: 레시피 없음(404), 냉장고 없음(404 — `fridgeId`를 줬는데 존재하지 않는 경우)

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

**`RecipeIngredientResponse`**: `id`, `ingredientId`(매칭 안 되면 null), `ingredientNameText`, `quantityText`, `quantityValue`, `unit`, `matched`(boolean — ingredientId 유무와 동일), `inFridge`(Boolean \| null — fridgeId 문맥이 있을 때만 true/false, 없으면 null. `false`인 재료 옆에 "장바구니에 담기" 버튼을 두고 눌렀을 때 6.1로 그 재료 이름을 추가하면 됩니다)

**`RecipeSummaryResponse`** (목록/추천용, `RecipeResponse`에서 `instructions`/`sourceUrl`/`authorNickname`/`tags`/`ingredients` 제외 + `matchedIngredientCount`/`totalIngredientCount`(Integer, null 가능) 추가

---

## 6. 장보기 API (`/fridges/{fridgeId}/shopping-items`)

**전부 인증 필요.** 장보기 리스트는 **냉장고별로 별도** 관리됩니다(같은 냉장고를 공유하는 멤버끼리 하나의 리스트를 같이 봄).

### 6.1 장보기 항목 추가 — `POST /fridges/{fridgeId}/shopping-items`

레시피 상세(5.1/5.6)에서 `inFridge: false`로 표시된 재료의 "장바구니에 담기" 버튼을 누르면, 그 재료의 `ingredientNameText`를 `name`으로 그대로 이 API에 보내면 됩니다. 재료 추가 화면과 동일하게 카테고리별 추천 재료(4.3)를 보여주고 그 이름을 그대로 써도 됩니다 — 이 API는 영양정보 추정 없이 이름만 저장하는 단순 리스트입니다.

**Request Body** (`ShoppingItemAddRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| name | String | O |

**Response** `200 OK` — `GlobalResponse<ShoppingItemResponse>`

**에러**: 냉장고 없음(404)

---

### 6.2 장보기 목록 조회 — `GET /fridges/{fridgeId}/shopping-items`

미완료 항목이 먼저, 그다음 최신 등록순으로 정렬됩니다.

**Response** `200 OK` — `GlobalResponse<List<ShoppingItemResponse>>`

---

### 6.3 장보기 항목 체크 — `PATCH /fridges/{fridgeId}/shopping-items/{itemId}`

**Request Body** (`ShoppingItemCheckRequest`)

| 필드 | 타입 | 필수 |
|---|---|---|
| checked | Boolean | O |

**Response** `200 OK` — `GlobalResponse<ShoppingItemResponse>` · **에러**: 항목 없음(404)

---

### 6.4 장보기 항목 삭제 — `DELETE /fridges/{fridgeId}/shopping-items/{itemId}`

**Response** `204 No Content` · **에러**: 항목 없음(404)

### 공통 DTO — `ShoppingItemResponse`

| 필드 | 타입 |
|---|---|
| id | Long |
| name | String |
| checked | boolean |
| createdAt | LocalDateTime |

---

## 7. 쿠팡 최저가 검색 API (`/coupang`)

**인증 필요.** 쿠팡파트너스 Open API(상품검색)를 이용해 재료/상품명으로 쿠팡 상품을 검색합니다. 장보기 항목 화면에서 "최저가 확인" 버튼을 누르면 그 항목의 `name`으로 `keyword`를 채워 호출하면 됩니다.

### 7.1 쿠팡 최저가 검색 — `GET /coupang/search?keyword=&limit=`

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword | String | O | 검색어 (예: "양송이스프") |
| limit | Integer | X | 최대 개수 (기본 10, 최대 30) |

**Response** `200 OK` — `GlobalResponse<List<CoupangProductResponse>>` — 쿠팡이 반환하는 순서 그대로 내려줍니다(정렬 로직 없음).

**`CoupangProductResponse`**: `productId`, `name`, `price`(Long, 원), `imageUrl`, `productUrl`(쿠팡파트너스 링크 — 클릭 시 수수료 발생 가능), `rocket`(boolean, 로켓배송 여부), `freeShipping`(boolean). ⚠️ Java 필드명은 `isRocket`/`isFreeShipping`이지만 boolean getter라 JSON에는 `is` 접두사 없이 `rocket`/`freeShipping`으로 내려갑니다.

**참고**: 쿠팡 서버 오류/키 미설정/네트워크 실패 시 예외 대신 **빈 리스트**를 반환합니다(검색 결과 없음과 동일하게 처리 — 프론트에서 별도 에러 분기 불필요).

---

## 8. 영수증 인식 API (`/fridges/{fridgeId}/receipts`)

**인증 필요.** 영수증 사진을 Claude(비전)에게 분석시켜 식재료로 보이는 품목을 추출합니다. **이 API는 아무것도 저장하지 않는 미리보기입니다** — 인식 결과를 보여주고 사용자가 확인/수정한 다음, 아래 4. 식재료 API / 3. 냉장고 재료 API를 호출해서 실제로 등록해야 합니다.

### 8.1 영수증 스캔 — `POST /fridges/{fridgeId}/receipts/scan`

`multipart/form-data`로 이미지 파일 하나(`image`)를 보냅니다. JPEG/PNG/WEBP만 가능하고 최대 10MB입니다.

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| image | File | O | 영수증 사진 |

**Response** `200 OK` — `GlobalResponse<ReceiptScanResponse>`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "name": "비비고 왕교자",
        "quantityText": "500g",
        "quantityValue": 500,
        "unit": "g",
        "categoryNameGuess": "냉동식품",
        "matchedIngredientId": null,
        "matchedCategoryId": null
      }
    ]
  },
  "message": null
}
```

- `matchedIngredientId`가 있으면 이미 등록된 식재료와 이름이 정확히 일치(또는 포함관계로 부분 일치)한 것 — 그대로 그 `ingredientId`로 3.1(재료 추가)을 호출하면 됩니다.
- `matchedIngredientId`가 없으면 처음 보는 이름 — 4.4(식재료 등록)에 `name`/`categoryNameGuess`(그대로 `categoryName`으로 사용 가능)를 보내서 새로 만들고, 그 응답의 `id`로 3.1을 호출하세요. 이때도 이름이 같으면 내부적으로 기존 재료가 재사용되고, 완전히 새 이름이면 Claude가 영양정보까지 자동 추정합니다.
- 영수증에 축약되거나 코드처럼 적힌 상품명(예: "국산돈목심600")은 Claude가 일반적으로 통용되는 이름(예: "돼지 목심")으로 풀어서 내려줍니다 — 완벽하지 않을 수 있으니 프론트에서 수정 가능하게 보여주는 걸 권장합니다.

**에러**: 냉장고 없음(404), 이미지 없음(400), 이미지 10MB 초과(400), 지원 안 하는 이미지 형식(400), 인식 실패(502 — Claude 호출 실패/크레딧 부족 등)

---

## 9. 식단 기록 API (`/meal-logs`, `/nutrition-goals`)

**전부 인증 필요.** `MealType`은 `BREAKFAST`/`LUNCH`/`DINNER`/`MORNING_SNACK`/`AFTERNOON_SNACK`/`EVENING_SNACK` 6개(하루 상세 조회는 항상 이 순서로 6개 슬롯을 전부 내려줌). `MealLogType`은 `RECIPE`(저장된 레시피에서 선택) / `FREEFORM`(재료 직접입력) 둘 중 하나.

### 9.1 식단 기록 추가 — `POST /meal-logs`

`logType`에 따라 요청 형태가 다릅니다.

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| mealDate | String(yyyy-MM-dd) | O | 기록할 날짜 |
| mealType | String | O | `BREAKFAST`/`LUNCH`/`DINNER`/`MORNING_SNACK`/`AFTERNOON_SNACK`/`EVENING_SNACK` |
| logType | String | O | `RECIPE` 또는 `FREEFORM` |
| recipeId | Long | logType=RECIPE일 때 필수 | 5번(레시피) API로 조회한 레시피 id |
| servings | Number | X (기본 1) | 몇 인분 먹었는지 (레시피의 1인분 영양정보 × servings로 계산) |
| items | Array | logType=FREEFORM일 때 필수(1개 이상) | `{ingredientId, quantity, unit}` 목록. **`ingredientId`는 4.4(식재료 등록)로 미리 만든/재사용한 id를 써야 함** — 이름으로 즉석 등록되지 않음 |

레시피로 기록하는 예:
```json
{ "mealDate": "2026-09-17", "mealType": "LUNCH", "logType": "RECIPE", "recipeId": 12, "servings": 1 }
```

직접 입력으로 기록하는 예:
```json
{
  "mealDate": "2026-09-17", "mealType": "MORNING_SNACK", "logType": "FREEFORM",
  "items": [{ "ingredientId": 34, "quantity": 1, "unit": "개" }]
}
```

**Response** `200 OK` — `GlobalResponse<MealLogResponse>` (`totalCalories`/`totalCarbohydrateG`/`totalProteinG`/`totalFatG`는 서버가 자동 계산. FREEFORM이면 `items`에 항목별 계산값도 함께 내려옴. 단위가 재료의 기준 단위와 다르면 — 예: 재료 기준은 g인데 quantity 단위가 "개" — 해당 항목의 영양정보는 null)

**에러**: RECIPE인데 recipeId 없음(400), 레시피 없음(404), FREEFORM인데 items 없음(400), 식재료 없음(404)

### 9.2 하루 식단 상세 조회 — `GET /meal-logs?date=`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| date | O | 조회할 날짜 (yyyy-MM-dd) |

**Response** `200 OK` — `GlobalResponse<DailyMealLogResponse>`

```json
{
  "success": true,
  "data": {
    "date": "2026-09-17",
    "totalCalories": 620, "totalCarbohydrateG": 55.0, "totalProteinG": 40.0, "totalFatG": 22.0,
    "targetCalories": 1696, "targetCarbohydrateG": 233.2, "targetProteinG": 106.0, "targetFatG": 37.7,
    "meals": [
      { "mealType": "BREAKFAST", "totalCalories": 0, "totalCarbohydrateG": 0, "totalProteinG": 0, "totalFatG": 0, "logs": [] },
      { "mealType": "LUNCH", "totalCalories": 620, "totalCarbohydrateG": 55.0, "totalProteinG": 40.0, "totalFatG": 22.0,
        "logs": [{ "id": 1, "mealDate": "2026-09-17", "mealType": "LUNCH", "logType": "RECIPE", "recipeId": 12, "recipeTitle": "돼지목살 스테이크", "servings": 1, "totalCalories": 620, "totalCarbohydrateG": 55.0, "totalProteinG": 40.0, "totalFatG": 22.0, "items": [], "createdAt": "2026-09-17T12:31:00" }] },
      { "mealType": "DINNER", "totalCalories": 0, "totalCarbohydrateG": 0, "totalProteinG": 0, "totalFatG": 0, "logs": [] },
      { "mealType": "MORNING_SNACK", "totalCalories": 0, "totalCarbohydrateG": 0, "totalProteinG": 0, "totalFatG": 0, "logs": [] },
      { "mealType": "AFTERNOON_SNACK", "totalCalories": 0, "totalCarbohydrateG": 0, "totalProteinG": 0, "totalFatG": 0, "logs": [] },
      { "mealType": "EVENING_SNACK", "totalCalories": 0, "totalCarbohydrateG": 0, "totalProteinG": 0, "totalFatG": 0, "logs": [] }
    ]
  },
  "message": null
}
```

- `meals`는 항상 6개 전부 내려옵니다(기록 없는 슬롯도 `logs: []`로 포함) — 프론트에서 그대로 6줄 리스트로 렌더링하면 됩니다.
- `target*` 필드는 그 날짜 기준으로 적용 중인 목표가 없으면 전부 `null`입니다(9.4 참고).

### 9.3 달력 요약 조회 — `GET /meal-logs/calendar?startDate=&endDate=`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| startDate | O | 조회 시작 날짜 |
| endDate | O | 조회 종료 날짜 |

**Response** `200 OK` — `GlobalResponse<List<DailyMealSummaryResponse>>`. **기록이 있는 날짜만** 포함됩니다(빈 날짜는 배열에 없음 — 프론트에서 달력 셀에 아무것도 안 그리면 됨).

```json
{
  "success": true,
  "data": [
    {
      "date": "2026-09-15",
      "totalCalories": 1180,
      "meals": [
        { "mealType": "LUNCH", "label": "돼지목살 구이" },
        { "mealType": "DINNER", "label": "된장찌개" }
      ]
    }
  ],
  "message": null
}
```

- `label`은 `logType=RECIPE`면 레시피 제목, `FREEFORM`이면 "첫 재료명 외 N개"(재료 1개면 그냥 이름).

### 9.4 식단 기록 삭제 — `DELETE /meal-logs/{mealLogId}`

본인이 기록한 것만 삭제 가능합니다. **에러**: 없거나 본인 기록이 아님(404)

### 9.5 목표 영양정보 등록 — `POST /nutrition-goals`

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| targetCalories | Integer | X | 목표 칼로리(kcal) |
| targetCarbohydrateG / targetProteinG / targetFatG | Number | X | 목표 탄/단/지(g) |
| weightKg / heightCm | Number | X | 체중/키 (목표 자동계산에 참고용, 서버가 자동계산하진 않음 — 프론트/사용자가 직접 계산해서 넣거나 향후 추가) |
| activityLevel | String | X | 활동량(자유 문자열, 예: "LOW"/"MEDIUM"/"HIGH") |
| effectiveDate | String(yyyy-MM-dd) | X (기본 오늘) | 이 목표가 적용되기 시작하는 날짜 |

새로 등록해도 기존 값을 덮어쓰지 않고 **이력으로 계속 쌓입니다** — 조회 시(9.2, 9.6) 항상 "그 날짜 기준으로 유효한(effectiveDate가 그 날짜 이하인 것 중 가장 최근) 목표"가 자동으로 선택됩니다. 그냥 목표를 갱신하고 싶으면 `effectiveDate`를 오늘로 새로 등록하면 됩니다.

**Response** `200 OK` — `GlobalResponse<NutritionGoalResponse>`

### 9.6 현재 목표 조회 — `GET /nutrition-goals/current?date=`

| 파라미터 | 필수 | 설명 |
|---|---|---|
| date | X (기본 오늘) | 이 날짜 기준으로 적용 중인 목표를 조회 |

**Response** `200 OK` — `GlobalResponse<NutritionGoalResponse>`. **설정된 목표가 없으면 `data`가 `null`**(에러 아님) — 프론트에서 "목표를 설정해주세요" 화면으로 처리하면 됩니다.

---

## 아직 구현되지 않은 것

- 소셜 로그인(카카오/네이버/구글/애플), 이메일 인증, 비밀번호 재설정
- **`FridgeItem`/레시피/장보기 API들의 멤버 권한 검증** — 현재 냉장고 존재 여부만 확인하고 요청자가 해당 냉장고 멤버인지는 확인하지 않음 (2번 냉장고 API는 이미 `FridgeMember` 기반 검증 적용됨)
- 로그아웃 시 access token 즉시 무효화 (현재는 만료시간까지 유효한 stateless JWT 한계 그대로)
- **식단 캘린더 프론트(cookgenieWeb)** — 백엔드 API(9번)는 완료, "오늘 상세"/"달력" 두 화면은 아직 미구현
- 레시피 좋아요/저장(`likeCount`/`saveCount` 증가), 조회수 집계
- **사진으로 재료 자동 등록 — 나머지 2단계**: 8번(영수증 스캔)에 이어서, (1) 쿠팡/네이버 등 구매내역 캡처 화면 인식, (2) 실물 상품 사진(포장지 등) 촬영으로 이름/카테고리 자동 인식 — 둘 다 8번과 같은 Claude 비전 + tool-use 패턴으로 확장 가능
