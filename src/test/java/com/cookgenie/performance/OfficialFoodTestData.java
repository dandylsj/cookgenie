package com.cookgenie.performance;

import com.cookgenie.domain.ingredient.external.OfficialFoodCandidate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 식약처 가공식품 데이터(실제 약 59만 건)와 비슷한 모양의 가짜 데이터를 결정적으로(seed 고정) 만든다.
 * 실제 데이터처럼 "흔한 식품명은 아주 많이, 드문 식품명은 몇 건만" 나오도록 식품명을 Zipf 분포로 뽑는다 -
 * 그래야 "결과가 많은 검색어"와 "결과가 거의 없는 검색어"의 성능 차이를 둘 다 볼 수 있다.
 */
final class OfficialFoodTestData {

    static final int REAL_DATASET_SIZE = 590_542;
    private static final int RARE_PRODUCT_INTERVAL = 50_000;

    /** 앞쪽일수록 자주 등장(Zipf). 맨 뒤쪽 몇 개는 전체 59만 건 중 수십 건 수준으로만 나온다. */
    private static final String[] FOODS = {
            "라면", "과자", "음료", "우유", "두부", "김치", "만두", "소시지", "햄", "빵",
            "요거트", "아이스크림", "커피", "주스", "치즈", "어묵", "떡", "국수", "냉면", "쌀국수",
            "닭가슴살", "돈까스", "피자", "핫도그", "볶음밥", "카레", "짜장", "짬뽕", "떡볶이", "순대",
            "김밥", "샐러드", "초콜릿", "사탕", "젤리", "비스킷", "쿠키", "케이크", "머핀", "도넛",
            "참치", "연어", "고등어", "새우", "오징어", "멸치", "김", "미역", "다시마", "간장",
            "된장", "고추장", "쌈장", "케첩", "마요네즈", "드레싱", "식초", "올리브유", "참기름", "들기름",
            "시리얼", "그래놀라", "견과", "아몬드", "호두", "땅콩버터", "잼", "꿀", "시럽", "푸딩",
            "곰탕", "육개장", "삼계탕", "갈비탕", "설렁탕", "미역국", "된장찌개", "김치찌개", "부대찌개", "순두부",
            "유린기", "꿔바로우", "깐풍기", "탕수육", "양장피", "마파두부", "딤섬", "훠궈", "마라탕", "우육면",
            "캐비어", "트뤼플", "푸아그라", "하몽", "살라미", "프로슈토", "브리오슈", "마카롱", "티라미수", "판나코타"
    };

    private static final String[] MODIFIERS = {
            "", "", "", "매콤한 ", "순한 ", "오리지널 ", "저당 ", "고단백 ", "실온보관 ", "냉동 ",
            "냉장 ", "유기농 ", "국산 ", "프리미엄 ", "간편 ", "미니 ", "대용량 ", "숯불 ", "훈제 ", "바삭한 "
    };

    private static final String[] SUFFIXES = {
            "", "", "", " 오리지널", " 매운맛", " 순한맛", " 치즈맛", " 불고기맛", " 갈릭", " 스파이시",
            " 플러스", " 라이트", " 골드", " 스페셜", " 클래식", " 1인분", " 컵", " 스틱", " 볼", " 바"
    };

    private static final String[] COMPANIES = {
            "한빛식품", "새봄푸드", "누리제과", "하늘유업", "바다수산", "들녘농산", "햇살식품", "온누리푸드", "참맛식품", "큰솥",
            "고향식품", "청정원료", "미소푸드", "해오름", "다온식품", "가온제과", "푸른들", "맑은샘유업", "대한냉동", "우리밀"
    };

    private OfficialFoodTestData() {
    }

    static List<OfficialFoodCandidate> generate(int fromIndex, int count) {
        return generate("P", fromIndex, count);
    }

    static List<OfficialFoodCandidate> generate(String foodCdPrefix, int fromIndex, int count) {
        Random random = new Random(42L + fromIndex);
        double[] cumulative = zipfCumulative(FOODS.length, 1.1);
        List<OfficialFoodCandidate> items = new ArrayList<>(count);
        for (int i = fromIndex; i < fromIndex + count; i++) {
            String food = FOODS[pick(cumulative, random.nextDouble())];
            String name = MODIFIERS[random.nextInt(MODIFIERS.length)] + food + SUFFIXES[random.nextInt(SUFFIXES.length)];
            if (i % RARE_PRODUCT_INTERVAL == RARE_PRODUCT_INTERVAL / 2) {
                // 59만 건 중 12건만 있는 아주 드문 상품 (특정 신제품 이름을 정확히 치는 경우를 재현)
                name = "트뤼플 초콜릿 한정판";
            }
            String company = COMPANIES[random.nextInt(COMPANIES.length)] + (random.nextInt(50) + 1);
            String mfr = switch (random.nextInt(3)) {
                case 0 -> company + "(주)";
                case 1 -> "주식회사 " + company;
                default -> company;
            };
            items.add(new OfficialFoodCandidate(
                    foodCdPrefix + i, name, mfr, "g",
                    50 + random.nextInt(500),
                    BigDecimal.valueOf(random.nextInt(800), 1),
                    BigDecimal.valueOf(random.nextInt(300), 1),
                    BigDecimal.valueOf(random.nextInt(300), 1),
                    BigDecimal.valueOf(random.nextInt(300), 1),
                    BigDecimal.valueOf(random.nextInt(20000), 1),
                    BigDecimal.valueOf(random.nextInt(100), 1)));
        }
        return items;
    }

    private static double[] zipfCumulative(int n, double s) {
        double[] weights = new double[n];
        double sum = 0;
        for (int k = 0; k < n; k++) {
            weights[k] = 1.0 / Math.pow(k + 1, s);
            sum += weights[k];
        }
        double acc = 0;
        for (int k = 0; k < n; k++) {
            acc += weights[k] / sum;
            weights[k] = acc;
        }
        return weights;
    }

    private static int pick(double[] cumulative, double r) {
        for (int k = 0; k < cumulative.length; k++) {
            if (r <= cumulative[k]) {
                return k;
            }
        }
        return cumulative.length - 1;
    }
}
