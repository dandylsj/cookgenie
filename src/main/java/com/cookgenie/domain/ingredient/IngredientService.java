package com.cookgenie.domain.ingredient;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.dto.IngredientSuggestionResponse;
import com.cookgenie.domain.ingredient.dto.IngredientUpdateRequest;
import com.cookgenie.domain.ingredient.dto.NutritionUpdateRequest;
import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.external.ClaudeNutritionClient;
import com.cookgenie.domain.ingredient.external.MfdsProcessedFoodClient;
import com.cookgenie.domain.ingredient.external.NutritionEstimate;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final IngredientRepository ingredientRepository;
    private final CategoryRepository categoryRepository;
    private final NutritionInfoRepository nutritionInfoRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final ClaudeNutritionClient claudeNutritionClient;
    private final MfdsProcessedFoodClient mfdsProcessedFoodClient;

    /**
     * 이름에 keyword가 포함된 식재료를 검색한다. keyword가 없으면 전체 목록을 반환한다.
     * categoryId를 주면 그 카테고리로 좁힌다 - 재료 추가 화면에서 카테고리를 고른 뒤 그 안에서
     * (정부 공식 데이터로 이미 채워진 재료 포함) 바로 검색해서 고를 수 있게 하는 용도.
     * 100g 기준 영양정보를 함께 내려준다.
     */
    @Transactional(readOnly = true)
    public List<IngredientResponse> searchIngredients(String keyword, Long categoryId) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<Ingredient> ingredients;
        if (categoryId != null && hasKeyword) {
            ingredients = ingredientRepository.findByCategoryIdAndNameContaining(categoryId, keyword);
        } else if (categoryId != null) {
            ingredients = ingredientRepository.findByCategoryId(categoryId);
        } else if (hasKeyword) {
            ingredients = ingredientRepository.findByNameContaining(keyword);
        } else {
            ingredients = ingredientRepository.findAll();
        }

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

    /**
     * 카테고리별 추천 재료 이름 목록(정적 데이터, DB/AI 호출 없음). 재료 추가 화면에서 카테고리를 고르면
     * 바로 보여줄 수 있고, 사용자가 그중 하나를 고르면 이 이름 그대로 {@link #createIngredient}를 호출하면 된다.
     */
    @Transactional(readOnly = true)
    public List<IngredientSuggestionResponse> getSuggestions(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException(ErrorMessage.CATEGORY_NOT_FOUND));
        return IngredientSuggestions.forCategory(category.getName()).stream()
                .map(IngredientSuggestionResponse::new)
                .toList();
    }

    /**
     * 목록에 없는 식재료를 등록한다.
     * 같은 이름의 식재료가 이미 있으면 새로 만들지 않고 그대로 재사용한다(중복 방지, 이미 있는 영양정보 재사용).
     * 완전히 새 이름이면 세 가지 경우로 나뉜다: (1) calories 등을 직접 줬으면 그 값을 그대로 저장(dataSource=USER_INPUT,
     * isVerified=true), (2) 안 줬지만 autoEstimateNutrition=true면 먼저 식약처 가공식품 공공데이터(이름 검색)를
     * 시도하고 매칭되면 그 값을 그대로 저장(dataSource=OFFICIAL_DB, isVerified=true) — 브랜드/상품명이 있는
     * 가공식품은 정부 실측값이 AI 추정보다 정확함. 못 찾으면 Claude에게 추정을 요청(dataSource=LLM_ESTIMATED),
     * (3) 둘 다 아니면 영양정보 없이 등록한다(dataSource=USER_INPUT, isVerified=false) — 매번 Claude를 호출하면
     * 토큰이 많이 들어서(특히 영수증 인식처럼 한 번에 여러 재료를 등록할 때), 기본은 호출 안 하고 필요할 때
     * PUT .../nutrition(직접 입력) 또는 POST .../nutrition/estimate(나중에 AI 추정)로 채우도록 함.
     */
    @Transactional
    public IngredientResponse createIngredient(IngredientCreateRequest request) {
        String name = request.getName().trim();

        List<Ingredient> existingMatches = ingredientRepository.findAllByName(name);
        if (!existingMatches.isEmpty()) {
            Ingredient existing = existingMatches.get(0);
            NutritionInfo existingNutrition = nutritionInfoRepository.findByIngredientId(existing.getId()).orElse(null);
            return new IngredientResponse(existing, existingNutrition);
        }

        Category category = findOrCreateCategory(request.getCategoryName());
        boolean hasManualNutrition = request.hasManualNutrition();
        boolean shouldEstimate = !hasManualNutrition && Boolean.TRUE.equals(request.getAutoEstimateNutrition());
        Optional<NutritionEstimate> officialEstimate = shouldEstimate
                ? mfdsProcessedFoodClient.search(name)
                : Optional.empty();
        Optional<NutritionEstimate> estimate = officialEstimate.isPresent()
                ? officialEstimate
                : (shouldEstimate ? claudeNutritionClient.estimate(name) : Optional.empty());

        DataSource dataSource = officialEstimate.isPresent() ? DataSource.OFFICIAL_DB
                : estimate.isPresent() ? DataSource.LLM_ESTIMATED
                : DataSource.USER_INPUT;
        Ingredient ingredient = ingredientRepository.save(
                Ingredient.builder()
                        .name(name)
                        .category(category)
                        .ingredientType(officialEstimate.isPresent() ? IngredientType.PROCESSED : IngredientType.RAW)
                        .defaultUnit(resolveDefaultUnit(request.getDefaultUnit(), request.getReferenceUnit(), estimate))
                        .dataSource(dataSource)
                        .isVerified(hasManualNutrition || officialEstimate.isPresent())
                        .build()
        );

        NutritionInfo nutritionInfo;
        if (hasManualNutrition) {
            nutritionInfo = saveManualNutritionInfo(ingredient, request);
        } else {
            nutritionInfo = estimate.map(e -> saveNutritionInfo(ingredient, e)).orElse(null);
        }

        return new IngredientResponse(ingredient, nutritionInfo);
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

    /**
     * 식재료의 기준량당 영양정보를 사용자가 직접 입력/수정한다(dataSource=USER_INPUT, isVerified=true).
     * 이미 있으면 덮어쓰고, 없으면 새로 만든다. AI 추정이 틀렸거나, 처음부터 안 채운 재료에 나중에 채워 넣을 때 쓴다.
     */
    @Transactional
    public IngredientResponse updateNutrition(Long ingredientId, NutritionUpdateRequest request) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new CustomException(ErrorMessage.INGREDIENT_NOT_FOUND));
        NutritionInfo nutritionInfo = nutritionInfoRepository.findByIngredientId(ingredientId).orElse(null);
        String referenceUnit = resolveManualReferenceUnit(request.getReferenceUnit(), nutritionInfo);

        if (nutritionInfo == null) {
            nutritionInfo = nutritionInfoRepository.save(
                    NutritionInfo.builder()
                            .ingredient(ingredient)
                            .referenceAmount(100)
                            .referenceUnit(referenceUnit)
                            .calories(request.getCalories())
                            .carbohydrateG(request.getCarbohydrateG())
                            .proteinG(request.getProteinG())
                            .fatG(request.getFatG())
                            .build()
            );
        } else {
            nutritionInfo.update(100, referenceUnit, request.getCalories(), request.getCarbohydrateG(),
                    request.getProteinG(), request.getFatG(),
                    nutritionInfo.getSugarG(), nutritionInfo.getSodiumMg(), nutritionInfo.getFiberG());
        }
        ingredient.markNutritionVerified();

        return new IngredientResponse(ingredient, nutritionInfo);
    }

    /**
     * 아직 영양정보가 없거나 다시 추정받고 싶은 식재료에 대해, 그 시점에 Claude로 영양정보 추정을 요청한다
     * (dataSource=LLM_ESTIMATED). 등록 시점에 자동으로 추정하지 않은 식재료를 나중에 채워 넣을 때 쓴다.
     */
    @Transactional
    public IngredientResponse estimateNutrition(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new CustomException(ErrorMessage.INGREDIENT_NOT_FOUND));
        NutritionEstimate estimate = claudeNutritionClient.estimate(ingredient.getName())
                .orElseThrow(() -> new CustomException(ErrorMessage.NUTRITION_ESTIMATION_FAILED));

        NutritionInfo nutritionInfo = nutritionInfoRepository.findByIngredientId(ingredientId).orElse(null);
        if (nutritionInfo == null) {
            nutritionInfo = saveNutritionInfo(ingredient, estimate);
        } else {
            nutritionInfo.update(100, estimate.referenceUnit(), estimate.calories(), estimate.carbohydrateG(),
                    estimate.proteinG(), estimate.fatG(), estimate.sugarG(), estimate.sodiumMg(), estimate.fiberG());
        }
        ingredient.markNutritionEstimated();

        return new IngredientResponse(ingredient, nutritionInfo);
    }

    /** 이름으로 식재료 마스터와 매칭을 시도한다. 정확히 일치하는 게 없으면 부분 일치라도 찾고, 그래도 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<Ingredient> matchByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        List<Ingredient> exact = ingredientRepository.findAllByName(trimmed);
        if (!exact.isEmpty()) {
            return Optional.of(exact.get(0));
        }
        List<Ingredient> partial = ingredientRepository.findByNameContaining(trimmed);
        return partial.isEmpty() ? Optional.empty() : Optional.of(partial.get(0));
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

    private NutritionInfo saveNutritionInfo(Ingredient ingredient, NutritionEstimate estimate) {
        NutritionInfo nutritionInfo = NutritionInfo.builder()
                .ingredient(ingredient)
                .referenceAmount(100)
                .referenceUnit(estimate.referenceUnit())
                .calories(estimate.calories())
                .carbohydrateG(estimate.carbohydrateG())
                .proteinG(estimate.proteinG())
                .fatG(estimate.fatG())
                .sugarG(estimate.sugarG())
                .sodiumMg(estimate.sodiumMg())
                .fiberG(estimate.fiberG())
                .build();
        return nutritionInfoRepository.save(nutritionInfo);
    }

    private NutritionInfo saveManualNutritionInfo(Ingredient ingredient, IngredientCreateRequest request) {
        NutritionInfo nutritionInfo = NutritionInfo.builder()
                .ingredient(ingredient)
                .referenceAmount(100)
                .referenceUnit(request.getReferenceUnit() != null && !request.getReferenceUnit().isBlank()
                        ? request.getReferenceUnit()
                        : "g")
                .calories(request.getCalories())
                .carbohydrateG(request.getCarbohydrateG())
                .proteinG(request.getProteinG())
                .fatG(request.getFatG())
                .build();
        return nutritionInfoRepository.save(nutritionInfo);
    }

    /** defaultUnit 우선순위: 명시적으로 준 값 > 수동 입력 영양정보의 기준 단위 > Claude 추정 기준 단위. */
    private String resolveDefaultUnit(String requestedUnit, String manualReferenceUnit, Optional<NutritionEstimate> estimate) {
        if (requestedUnit != null && !requestedUnit.isBlank()) {
            return requestedUnit;
        }
        if (manualReferenceUnit != null && !manualReferenceUnit.isBlank()) {
            return manualReferenceUnit;
        }
        return estimate.map(NutritionEstimate::referenceUnit).orElse(null);
    }

    private String resolveManualReferenceUnit(String requestedUnit, NutritionInfo existing) {
        if (requestedUnit != null && !requestedUnit.isBlank()) {
            return requestedUnit;
        }
        return existing != null && existing.getReferenceUnit() != null ? existing.getReferenceUnit() : "g";
    }

    /** OfficialNutritionSyncService(같은 패키지)에서도 재사용해서 카테고리 생성 로직을 하나로 유지한다. */
    Category findOrCreateCategory(String categoryName) {
        String name = (categoryName == null || categoryName.isBlank()) ? "기타" : categoryName;
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build()));
    }
}
