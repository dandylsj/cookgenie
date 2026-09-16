package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.external.ProcessedFoodCsvLoader;
import com.cookgenie.domain.ingredient.external.RawFoodCsvLoader;
import com.cookgenie.domain.ingredient.external.RawFoodCsvRow;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공공데이터포털 표준데이터(CSV)를 대표식품코드 기준으로 묶어서 재료 마스터에 시딩한다
 * (dataSource=OFFICIAL_DB, isVerified=true). 이미 등록된 이름(수동 입력이든 AI 추정이든)은 덮어쓰지
 * 않고 건너뛴다 — "아직 아무 영양정보도 없는" 재료를 정부 공식 데이터로 미리 채워두는 용도이기 때문.
 *
 * <p>원재료성식품({@link RawFoodCsvLoader})과 가공식품({@link ProcessedFoodCsvLoader})은 완전히
 * 별도로 동기화한다 - 두 표준데이터는 대표식품코드의 번호 체계가 데이터구분(R/P)마다 다른 뜻으로
 * 재사용되기 때문에 하나의 맵에 섞어서 그룹핑하면 서로 다른 음식이 같은 코드로 충돌할 수 있다.
 * 대신 최종적으로는 둘 다 "이미 등록된 이름이면 건너뛴다"는 같은 규칙을 타므로, 두 데이터셋에 우연히
 * 같은 이름이 있어도(예: 원재료 "우유" vs 가공식품 "우유") 먼저 동기화한 쪽이 그대로 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialNutritionSyncService {

    private static final String PREFERRED_STATE = "생것";

    /** 원재료성식품 CSV의 식품대분류명 -> 기존 앱 카테고리명. 매핑에 없으면 CSV 이름을 그대로 새 카테고리로 만든다. */
    private static final Map<String, String> RAW_CATEGORY_MAP = Map.ofEntries(
            Map.entry("곡류", "곡류·떡·빵"),
            Map.entry("감자 및 전분류", "채소"),
            Map.entry("두류", "달걀·두부·콩"),
            Map.entry("견과 및 종실류", "간식·디저트"),
            Map.entry("채소류", "채소"),
            Map.entry("버섯류", "채소"),
            Map.entry("과일류", "과일"),
            Map.entry("해조류", "해산물"),
            Map.entry("육류", "육류"),
            Map.entry("난류", "달걀·두부·콩"),
            Map.entry("어패류 및 기타 수산물", "해산물"),
            Map.entry("우유류", "유제품"),
            Map.entry("유지류", "양념·소스"),
            Map.entry("당류", "간식·디저트"),
            Map.entry("조미료류", "양념·소스"),
            Map.entry("차류", "음료·주류")
    );

    /** 가공식품 CSV의 식품대분류명 -> 기존 앱 카테고리명. */
    private static final Map<String, String> PROCESSED_CATEGORY_MAP = Map.ofEntries(
            Map.entry("조미식품", "양념·소스"),
            Map.entry("장류", "양념·소스"),
            Map.entry("식용유지류", "양념·소스"),
            Map.entry("절임류 또는 조림류", "양념·소스"),
            Map.entry("과자류·빵류 또는 떡류", "간식·디저트"),
            Map.entry("잼류", "간식·디저트"),
            Map.entry("당류", "간식·디저트"),
            Map.entry("빙과류", "간식·디저트"),
            Map.entry("코코아가공품류 또는 초콜릿류", "간식·디저트"),
            Map.entry("벌꿀 및 화분가공 식품류", "간식·디저트"),
            Map.entry("음료류", "음료·주류"),
            Map.entry("주류", "음료·주류"),
            Map.entry("면류", "면류"),
            Map.entry("유가공품류", "유제품"),
            Map.entry("알가공품류", "달걀·두부·콩"),
            Map.entry("두부류 또는 묵류", "달걀·두부·콩"),
            Map.entry("식육가공품 및 포장육", "육류"),
            Map.entry("동물성가공식품류", "육류"),
            Map.entry("수산가공식품류", "해산물"),
            Map.entry("농산가공식품류", "가공식품"),
            Map.entry("즉석식품류", "가공식품"),
            Map.entry("기타식품류", "가공식품"),
            Map.entry("특수영양식품", "가공식품"),
            Map.entry("특수의료용도식품", "가공식품")
    );

    private final RawFoodCsvLoader rawFoodCsvLoader;
    private final ProcessedFoodCsvLoader processedFoodCsvLoader;
    private final IngredientRepository ingredientRepository;
    private final NutritionInfoRepository nutritionInfoRepository;
    private final IngredientService ingredientService;

    @Transactional
    public SyncResult syncFromCsv() {
        return sync(rawFoodCsvLoader.loadAll(), RAW_CATEGORY_MAP, IngredientType.RAW, "식약처 원재료 CSV 동기화");
    }

    @Transactional
    public SyncResult syncProcessedFoodsFromCsv() {
        return sync(processedFoodCsvLoader.loadAll(), PROCESSED_CATEGORY_MAP, IngredientType.PROCESSED, "식약처 가공식품 CSV 동기화");
    }

    private SyncResult sync(List<RawFoodCsvRow> rows, Map<String, String> categoryMap, IngredientType ingredientType,
                             String logLabel) {
        // 대표식품코드로 묶어서 "생것"이 있으면 그걸, 없으면 먼저 나온 것을 대표값으로 남긴다.
        Map<String, RawFoodCsvRow> representativeByCode = new LinkedHashMap<>();
        for (RawFoodCsvRow row : rows) {
            if (row.representativeName() == null || row.representativeName().isBlank()) {
                continue;
            }
            String key = row.representativeCode() != null ? row.representativeCode() : row.representativeName();
            RawFoodCsvRow current = representativeByCode.get(key);
            if (current == null || isPreferredOver(row, current)) {
                representativeByCode.put(key, row);
            }
        }

        int created = 0;
        int skipped = 0;
        for (RawFoodCsvRow row : representativeByCode.values()) {
            String name = row.representativeName().trim();
            if (name.isEmpty() || !ingredientRepository.findAllByName(name).isEmpty()) {
                skipped++;
                continue;
            }
            saveIngredient(name, row, categoryMap, ingredientType);
            created++;
        }

        SyncResult result = new SyncResult(rows.size(), representativeByCode.size(), created, skipped);
        log.info("[{}] {}", logLabel, result);
        return result;
    }

    private boolean isPreferredOver(RawFoodCsvRow candidate, RawFoodCsvRow current) {
        boolean candidateIsRaw = PREFERRED_STATE.equals(candidate.stateName());
        boolean currentIsRaw = PREFERRED_STATE.equals(current.stateName());
        return candidateIsRaw && !currentIsRaw;
    }

    private void saveIngredient(String name, RawFoodCsvRow row, Map<String, String> categoryMap, IngredientType ingredientType) {
        String categoryName = categoryMap.getOrDefault(row.categoryName(), row.categoryName());
        Category category = ingredientService.findOrCreateCategory(categoryName);
        String unit = row.referenceUnit() != null ? row.referenceUnit() : "g";

        Ingredient ingredient = ingredientRepository.save(
                Ingredient.builder()
                        .name(name)
                        .category(category)
                        .ingredientType(ingredientType)
                        .defaultUnit(unit)
                        .dataSource(DataSource.OFFICIAL_DB)
                        .isVerified(true)
                        .build()
        );

        nutritionInfoRepository.save(
                NutritionInfo.builder()
                        .ingredient(ingredient)
                        .referenceAmount(row.referenceAmount() != null ? row.referenceAmount() : 100)
                        .referenceUnit(unit)
                        .calories(row.calories())
                        .carbohydrateG(row.carbohydrateG())
                        .proteinG(row.proteinG())
                        .fatG(row.fatG())
                        .sugarG(row.sugarG())
                        .sodiumMg(row.sodiumMg())
                        .fiberG(row.fiberG())
                        .build()
        );
    }

    public record SyncResult(int totalRows, int normalizedGroups, int created, int skipped) {
    }
}
