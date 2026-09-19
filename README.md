# 🍳 CookGenie

냉장고 속 재료를 등록하면 유통기한을 관리해주고, 그 재료로 만들 수 있는 요리까지 AI가 추천해주는 백엔드 서비스입니다.

> 방치하다 버려지는 냉장고 재료를 줄이고 싶어서 시작한 개인 프로젝트입니다. 기획부터 백엔드 개발, 서버 배포까지 혼자 진행했습니다.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8-blue?logo=mysql)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Blue--Green-2496ED?logo=docker)](https://www.docker.com/)
[![Deploy](https://github.com/dandylsj/cookgenie/actions/workflows/deploy-to-ubuntu.yml/badge.svg)](https://github.com/dandylsj/cookgenie/actions/workflows/deploy-to-ubuntu.yml)

## 🔗 링크

| | |
|---|---|
| 배포 서버 (API) | [cookgenie.dandyhomelab.uk](https://cookgenie.dandyhomelab.uk) |
| API 문서 (Swagger) | [cookgenie.dandyhomelab.uk/swagger-ui/index.html](https://cookgenie.dandyhomelab.uk/swagger-ui/index.html) |
| 프론트엔드 (웹) | [cookgenie-web.vercel.app](https://cookgenie-web.vercel.app) |
| 프론트엔드 저장소 | [dandylsj/cookgenieweb](https://github.com/dandylsj/cookgenieweb) |
| 전체 API 명세 | [docs/API.md](./docs/API.md) |

## 📌 소개

CookGenie는 "냉장고 재료 관리"와 "AI 레시피 추천"을 하나로 묶은 서비스입니다. 재료를 등록해두면 소비기한이 임박했을 때 알 수 있고, 지금 냉장고에 있는 재료만으로 만들 수 있는 요리를 AI가 바로 만들어줍니다. 영수증이나 상품 사진 한 장만 찍으면 재료를 자동으로 인식해서 등록까지 이어지고, 회원가입 없이도 게스트로 바로 모든 기능을 체험해볼 수 있습니다.

## 🛠 기술 스택

- **Backend**: Java 17, Spring Boot 4.1.0, Spring Data JPA, Spring Security, JWT
- **DB**: MySQL 8 (Flyway 도입은 해뒀으나 현재는 `ddl-auto: update` 사용)
- **AI**: Anthropic Claude API (tool-use 기반 구조화 응답 / 비전 OCR / 웹 검색)
- **외부 연동**: YouTube Data API v3, 쿠팡파트너스 Open API, 식약처 공공데이터(data.go.kr), 카카오·구글 OAuth
- **인프라**: Docker(Blue-Green 무중단 배포), GitHub Actions(self-hosted runner), Cloudflare Tunnel

## 🏗 배포 아키텍처

개인 Ubuntu 서버에 Docker 기반 Blue-Green 배포를 직접 구성했습니다. `dev` 브랜치에 머지되면 GitHub Actions(self-hosted runner)가 새 이미지를 빌드해 idle 상태인 컨테이너(blue/green 중 하나)에 띄우고, 헬스체크 통과 후 트래픽을 전환해 무중단으로 배포합니다. 외부 노출은 Cloudflare Tunnel로 처리해 공인 IP·포트포워딩 없이 `cookgenie.dandyhomelab.uk` 도메인을 운영합니다.

## ✨ 주요 기능

| 도메인 | 설명 |
|---|---|
| 인증 | 회원가입/로그인/JWT 재발급 · **카카오·구글 소셜 로그인** · **게스트 모드**(가입 없이 체험, 3일 후 자동 삭제 / 이후 정식 전환 시 데이터 유지) |
| 냉장고 | 생성/삭제, **4자리 초대코드로 멤버 공유**, 멤버 목록/강퇴/탈퇴 |
| 재료 관리 | CRUD, 수량 기준 탄단지 자동 계산, **등록 시 AI가 100g 기준 영양정보 자동 추정** |
| AI 레시피 | 냉장고 재료 기반 레시피 생성(재료/조리법/영양정보/태그), **유튜브 요리 영상 → 레시피 변환**, 재료 기반 추천 |
| 사진 인식 | 영수증 / 온라인 쇼핑몰 주문내역 캡처 / 실물 상품 사진 → **Claude Vision**으로 재료 후보 추출 + 공식 영양정보 자동 매칭 |
| 장보기 | 냉장고별 장보기 리스트 + **쿠팡파트너스 연동 최저가 검색** |
| 식단 기록 | 레시피/직접입력 기반 하루 식단 기록, 목표 칼로리·탄단지 설정 및 대비 조회 |
| 통계 | 소비기한 임박/지남, 카테고리별 분포, 등록 활동 히트맵 등 |

## 🧩 기술적 도전과 해결

개발하며 실제로 마주친 문제와 원인 분석 과정입니다. 전체 기록은 [CLAUDE.md](./CLAUDE.md)에 더 상세히 남겨뒀습니다.

**1. LLM이 스키마 요건은 만족시키면서 필수 필드를 비우는 문제**
AI 레시피의 조리 순서가 가끔 통째로 빈 채 저장되는 버그가 있었습니다. `max_tokens` 부족으로 응답이 잘린다고 가정하고 필드 순서를 조정해도 재발했는데, 다른 필드는 항상 정상 채워진다는 점에서 "잘림"이 아니라 **모델이 빈 배열(`[]`)로도 배열 타입 요건 자체는 통과시켜버린다**는 게 진짜 원인이었습니다. JSON 스키마에 `minItems`를 추가해 빈 배열을 스키마 위반으로 강제하고, 애플리케이션 레벨에도 재시도 로직을 추가해 이중으로 방어했습니다.

**2. 공공데이터 "대표코드"를 상품 고유 코드로 잘못 가정한 문제**
식약처 가공식품 데이터(약 59만 건)를 로컬 DB로 미러링했는데, 실제로는 24만 건만 저장됐습니다. 재실행해도 새로 저장되는 건수가 정확히 0건이라는 단서로 — 페이지네이션이 아니라 저장 시점의 중복 판단 로직 문제라고 추리했고, `foodCd`가 상품별 고유 코드가 아니라 여러 제조사·상품이 공유하는 "대표코드"라는 것을 확인했습니다. 유니크 제약을 `(food_cd, food_nm, mfr_nm)` 복합키로 바꿔 해결했습니다.

**3. 소셜 로그인 계정이 2개 이상이면 프로필 조회가 500으로 깨지는 문제**
프로필 조회가 JWT의 `loginId`로 사용자를 단건 조회하는 구조였는데, 카카오/구글 로그인 계정은 `loginId`를 채우지 않아 항상 `null`입니다. `findByLoginId(null)`이 `WHERE login_id IS NULL`로 동작해 소셜 계정이 1개일 땐 우연히 매칭됐지만, 2개가 되자 `NonUniqueResultException`으로 500이 발생했습니다. JWT의 `userId` 클레임으로 조회하도록 바꿔 근본 해결했습니다.

**4. "배포 성공"이 "시크릿 등록 완료"를 보장하지 않는 함정**
GitHub Actions에서 미등록 Secret을 참조해도 워크플로우는 실패하지 않고 빈 문자열로 치환됩니다. `.env` 생성 스크립트가 항상 그 줄을 쓰기 때문에 앱은 정상 기동하지만, 실제 기능(소셜 로그인 등)은 빈 값으로 호출돼 그 기능을 직접 써봐야만 문제가 드러납니다. 이후 새 외부 연동을 추가할 때마다 "배포 성공 ≠ 시크릿 정상 등록"을 전제로 실제 동작까지 검증하는 습관을 들였습니다.

## 🚀 로컬 실행

```bash
# 1) MySQL에 DB만 생성 (테이블은 앱 기동 시 자동 생성)
mysql -u root -p < sql/create_database.sql

# 2) src/main/resources/application-secrets.yml 생성 (git에 커밋되지 않는 파일)
#    DB_PASSWORD / JWT_SECRET_KEY / ANTHROPIC_API_KEY 등 필요한 값 채우기
#    (자세한 목록은 CLAUDE.md 참고)

# 3) 실행
./gradlew bootRun
```

기본 로그인 API는 `POST /auth/signup`이며, 별도 설정 없이 바로 체험하려면 `POST /auth/guest`(게스트 로그인)를 사용하면 됩니다. 전체 엔드포인트 명세는 [docs/API.md](./docs/API.md) 또는 실행 후 `/swagger-ui/index.html`에서 확인할 수 있습니다.

## 📁 도메인 구조

```
domain
├── auth       # 회원가입/로그인/JWT, 카카오·구글 소셜 로그인, 게스트 모드
├── user       # 사용자
├── fridge     # 냉장고, 멤버 공유(초대코드)
├── ingredient # 재료 마스터, AI/공공데이터 영양정보
├── recipe     # AI 레시피 생성, 유튜브 레시피 변환
├── receipt    # 영수증/주문내역 사진 인식
├── product    # 실물 상품 사진 인식
├── shopping   # 장보기 리스트, 쿠팡 최저가 검색
└── meallog    # 식단 기록, 목표 영양정보
```
