package com.cookgenie.domain.ingredient;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.dto.IngredientUpdateRequest;
import com.cookgenie.domain.ingredient.dto.RawMaterialSyncResponse;
import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.external.MfdsRawMaterialClient;
import com.cookgenie.domain.ingredient.external.MfdsRawMaterialItem;
import com.cookgenie.domain.ingredient.external.MfdsRawMaterialResponse;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 식재료 마스터(Ingredient) 조회/검색/등록을 담당하는 서비스. 냉장고 재료(FridgeItem)가 참조하는 식재료 카탈로그. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientService {

    private static final Pattern REFERENCE_AMOUNT_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");
    private static final int SYNC_PAGE_SIZE = 1000;

    private final IngredientRepository ingredientRepository;
    private final CategoryRepository categoryRepository;
    private final NutritionInfoRepository nutritionInfoRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final MfdsRawMaterialClient mfdsRawMaterialClient;

    /** 이름에 keyword가 포함된 식재료를 검색한다. keyword가 없으면 전체 목록을 반환한다. 100g 기준 영양정보를 함께 내려준다. */
    @Transactional(readOnly = true)
    public List<IngredientResponse> searchIngredients(String keyword) {
        List<Ingredient> ingredients = (keyword == null || keyword.isBlank())
                ? ingredientRepository.findAll()
                : ingredientRepository.findByNameContaining(keyword);

        List<Long> ingredientIds = ingredients.stream().map(Ingredient::getId).toList();
        Map<Long, NutritionInfo> nutritionByIngredientId = nutritionInfoRepository.findByIngredientIdIn(ingredientIds)
                .stream()
                .collect(Collectors.toMap(n -> n.getIngredient().getId(), n -> n));

        return ingredients.stream()
                .map(ingredient -> new IngredientResponse(ingredient, nutritionByIngredientId.get(ingredient.getId())))
                .toList();
    }

    /** 식재료 카테고리 전체 목록 조회. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream().map(CategoryResponse::new).toList();
    }

    /** 목록에 없는 식재료를 사용자가 직접 등록한다. 같은 이름의 카테고리가 있으면 재사용하고, 없으면 새로 만든다. */
    @Transactional
    public IngredientResponse createIngredient(IngredientCreateRequest request) {
        Category category = findOrCreateCategory(request.getCategoryName());

        Ingredient ingredient = Ingredient.builder()
                .name(request.getName())
                .category(category)
                .ingredientType(IngredientType.RAW)
                .defaultUnit(request.getDefaultUnit())
                .dataSource(DataSource.USER_INPUT)
                .isVerified(false)
                .build();

        return new IngredientResponse(ingredientRepository.save(ingredient), null);
    }

    /** 식재료 이름/카테고리/기본 단위를 수정한다. 잘못 고른 카테고리를 바로잡을 때 사용한다. */
    @Transactional
    public IngredientResponse updateIngredient(Long ingredientId, IngredientUpdateRequest request) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new CustomException(ErrorMessage.INGREDIENT_NOT_FOUND));
        Category category = findOrCreateCategory(request.getCategoryName());
        ingredient.update(request.getName(), category, request.getDefaultUnit());
        NutritionInfo nutritionInfo = nutritionInfoRepository.findByIngredientId(ingredientId).orElse(null);
        return new IngredientResponse(ingredient, nutritionInfo);
    }

    /** 식재료를 삭제한다. 이미 어떤 냉장고에 등록되어 있는 식재료는 삭제할 수 없다. */
    @Transactional
    public void deleteIngredient(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new CustomException(ErrorMessage.INGREDIENT_NOT_FOUND));
        if (fridgeItemRepository.existsByIngredientId(ingredientId)) {
            throw new CustomException(ErrorMessage.INGREDIENT_IN_USE);
        }
        ingredientRepository.delete(ingredient);
    }

    /**
     * 농촌진흥청 원재료성 식품 영양성분 Open API 전체를 페이지 단위로 가져와 Ingredient/NutritionInfo를 채운다.
     * 이 API는 이름 검색 필터를 지원하지 않아(NODATA_ERROR) 전량을 순회하는 배치 방식으로 동작한다.
     * 이름이 이미 존재하면 카테고리/영양정보를 최신값으로 갱신한다(재실행해도 안전).
     */
    @Transactional
    public RawMaterialSyncResponse syncRawMaterialsFromMfds() {
        Map<String, Category> categoryCache = new HashMap<>();
        int pageNo = 1;
        int totalFetched = 0;
        int created = 0;
        int updated = 0;
        int failed = 0;

        while (true) {
            MfdsRawMaterialResponse response = mfdsRawMaterialClient.fetchPage(pageNo, SYNC_PAGE_SIZE);
            List<MfdsRawMaterialItem> items = extractItems(response);
            if (items.isEmpty()) {
                break;
            }

            for (MfdsRawMaterialItem item : items) {
                totalFetched++;
                try {
                    if (upsertRawMaterial(item, categoryCache)) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("[MFDS 동기화] 항목 저장 실패 - foodNm={}, error={}", item.foodNm(), e.getMessage());
                }
            }

            Integer totalCount = response.body().totalCount();
            if (totalCount == null || (long) pageNo * SYNC_PAGE_SIZE >= totalCount) {
                break;
            }
            pageNo++;
        }

        log.info("[MFDS 동기화] 완료 - 조회 {}건, 생성 {}건, 갱신 {}건, 실패 {}건", totalFetched, created, updated, failed);
        return new RawMaterialSyncResponse(totalFetched, created, updated, failed);
    }

    private List<MfdsRawMaterialItem> extractItems(MfdsRawMaterialResponse response) {
        if (response == null || response.body() == null || response.body().items() == null
                || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item();
    }

    /** 식품명을 기준으로 있으면 갱신, 없으면 새로 만든다. true를 반환하면 신규 생성. */
    private boolean upsertRawMaterial(MfdsRawMaterialItem item, Map<String, Category> categoryCache) {
        String name = item.foodNm();
        if (name == null || name.isBlank()) {
            return false;
        }

        Category category = categoryCache.computeIfAbsent(item.foodLv3Nm(), this::findOrCreateCategory);
        String[] referenceAmountAndUnit = parseReferenceAmountAndUnit(item.nutConSrtrQua());

        List<Ingredient> existingMatches = ingredientRepository.findAllByName(name);
        boolean isNew = existingMatches.isEmpty();

        Ingredient ingredient;
        if (isNew) {
            ingredient = ingredientRepository.save(
                    Ingredient.builder()
                            .name(name)
                            .category(category)
                            .ingredientType(IngredientType.RAW)
                            .defaultUnit(referenceAmountAndUnit[1])
                            .dataSource(DataSource.OFFICIAL_DB)
                            .isVerified(true)
                            .build()
            );
        } else {
            if (existingMatches.size() > 1) {
                log.warn("[MFDS 동기화] 이름이 중복된 식재료 발견 - name={}, ids={} (OFFICIAL_DB 항목을 우선 갱신)",
                        name, existingMatches.stream().map(Ingredient::getId).toList());
            }
            ingredient = existingMatches.stream()
                    .filter(i -> i.getDataSource() == DataSource.OFFICIAL_DB)
                    .findFirst()
                    .orElse(existingMatches.get(0));
            ingredient.update(name, category, referenceAmountAndUnit[1]);
        }

        NutritionInfo nutritionInfo = nutritionInfoRepository.findByIngredientId(ingredient.getId())
                .orElseGet(() -> NutritionInfo.builder().ingredient(ingredient).build());

        nutritionInfo.update(
                parseInt(referenceAmountAndUnit[0], 100),
                referenceAmountAndUnit[1],
                parseInt(item.enerc(), null),
                parseDecimal(item.chocdf()),
                parseDecimal(item.prot()),
                parseDecimal(item.fatce()),
                parseDecimal(item.sugar()),
                parseDecimal(item.nat()),
                parseDecimal(item.fibtg())
        );
        nutritionInfoRepository.save(nutritionInfo);

        return isNew;
    }

    private Category findOrCreateCategory(String categoryName) {
        String name = (categoryName == null || categoryName.isBlank()) ? "기타" : categoryName;
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build()));
    }

    /** "100g", "100ml" 같은 문자열을 [수량, 단위]로 분리한다. 형식이 안 맞으면 기본값(100, g)을 쓴다. */
    private String[] parseReferenceAmountAndUnit(String raw) {
        if (raw == null) {
            return new String[]{"100", "g"};
        }
        Matcher matcher = REFERENCE_AMOUNT_PATTERN.matcher(raw.trim());
        if (matcher.matches()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return new String[]{"100", "g"};
    }

    private Integer parseInt(String raw, Integer defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return (int) Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
