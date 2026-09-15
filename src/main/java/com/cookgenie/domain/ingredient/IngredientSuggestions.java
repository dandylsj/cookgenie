package com.cookgenie.domain.ingredient;

import java.util.List;
import java.util.Map;

/**
 * 재료 추가 화면에서 카테고리를 고르면 보여줄, 자주 쓰는 재료 이름 목록(정적 데이터).
 * DB나 Claude 호출 없이 즉시 응답하기 위한 순수 참고용 목록이다 — 실제 등록/영양정보 추정은
 * 사용자가 이름을 고른 뒤 기존 {@code POST /ingredients}(같은 이름 재사용 또는 신규 추정) 흐름을 그대로 탄다.
 * 이름을 항상 똑같은 문자열로 고정해두면, 여러 사용자가 같은 재료를 골라도 "돼지고기" vs "돼지 고기" 같은
 * 표기 차이로 중복 등록/중복 추정이 생기는 걸 막을 수 있다.
 *
 * {@code CategorySeeder}의 기본 카테고리 이름과 맞춰뒀다 — 카테고리가 늘어나면 여기도 같이 늘려야 한다.
 */
final class IngredientSuggestions {

    private static final Map<String, List<String>> BY_CATEGORY = Map.ofEntries(
            Map.entry("육류", List.of(
                    "돼지고기", "돼지 목살", "삼겹살", "돼지 앞다리살", "소고기", "소 등심", "소 안심",
                    "차돌박이", "다짐육", "닭가슴살", "닭다리살", "닭날개", "닭 목살", "오리고기"
            )),
            Map.entry("해산물", List.of(
                    "고등어", "갈치", "삼치", "명태", "대구", "멸치", "조기", "새우", "오징어",
                    "문어", "낙지", "꽃게", "연어", "참치", "굴", "전복"
            )),
            Map.entry("달걀·두부·콩", List.of(
                    "계란", "메추리알", "두부", "순두부", "콩나물", "숙주나물", "검은콩", "병아리콩"
            )),
            Map.entry("채소", List.of(
                    "대파", "양파", "마늘", "생강", "배추", "양배추", "상추", "깻잎", "시금치",
                    "고추", "당근", "감자", "고구마", "오이", "애호박", "브로콜리", "무", "버섯"
            )),
            Map.entry("과일", List.of(
                    "사과", "바나나", "딸기", "포도", "귤", "오렌지", "수박", "참외", "배", "키위"
            )),
            Map.entry("유제품", List.of(
                    "우유", "요거트", "치즈", "생크림", "버터"
            )),
            Map.entry("곡류·떡·빵", List.of(
                    "쌀", "현미", "식빵", "떡", "밀가루"
            )),
            Map.entry("면류", List.of(
                    "소면", "칼국수면", "우동면", "파스타면", "당면"
            )),
            Map.entry("가공식품", List.of(
                    "스팸", "베이컨", "햄", "어묵", "소시지", "참치캔", "만두"
            )),
            Map.entry("양념·소스", List.of(
                    "소금", "후추", "간장", "고추장", "된장", "설탕", "식용유", "참기름",
                    "다진마늘", "케첩", "마요네즈", "굴소스"
            )),
            Map.entry("간식·디저트", List.of(
                    "초콜릿", "과자", "아이스크림", "젤리"
            )),
            Map.entry("음료·주류", List.of(
                    "생수", "콜라", "맥주", "소주", "와인"
            ))
    );

    private IngredientSuggestions() {
    }

    static List<String> forCategory(String categoryName) {
        return BY_CATEGORY.getOrDefault(categoryName, List.of());
    }
}
