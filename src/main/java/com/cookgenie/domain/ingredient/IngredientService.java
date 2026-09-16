package com.cookgenie.domain.ingredient;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.dto.IngredientSuggestionResponse;
import com.cookgenie.domain.ingredient.dto.IngredientUpdateRequest;
import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.external.ClaudeNutritionClient;
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
     * 완전히 새 이름이면 Claude에게 100g 기준 평균 영양정보를 추정시켜 함께 저장한다(dataSource=LLM_ESTIMATED,
     * isVerified=false). 추정에 실패하면 영양정보 없이 등록한다(dataSource=USER_INPUT).
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
        Optional<NutritionEstimate> estimate = claudeNutritionClient.estimate(name);

        Ingredient ingredient = ingredientRepository.save(
                Ingredient.builder()
                        .name(name)
                        .category(category)
                        .ingredientType(IngredientType.RAW)
                        .defaultUnit(resolveDefaultUnit(request.getDefaultUnit(), estimate))
                        .dataSource(estimate.isPresent() ? DataSource.LLM_ESTIMATED : DataSource.USER_INPUT)
                        .isVerified(false)
                        .build()
        );

        NutritionInfo nutritionInfo = estimate.map(e -> saveNutritionInfo(ingredient, e)).orElse(null);

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

    private String resolveDefaultUnit(String requestedUnit, Optional<NutritionEstimate> estimate) {
        if (requestedUnit != null && !requestedUnit.isBlank()) {
            return requestedUnit;
        }
        return estimate.map(NutritionEstimate::referenceUnit).orElse(null);
    }

    private Category findOrCreateCategory(String categoryName) {
        String name = (categoryName == null || categoryName.isBlank()) ? "기타" : categoryName;
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build()));
    }
}
