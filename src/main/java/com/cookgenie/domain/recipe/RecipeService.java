package com.cookgenie.domain.recipe;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.entity.FridgeItem;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.recipe.dto.AiRecipeGenerateRequest;
import com.cookgenie.domain.recipe.dto.RecipeResponse;
import com.cookgenie.domain.recipe.dto.RecipeSummaryResponse;
import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.recipe.entity.RecipeIngredient;
import com.cookgenie.domain.recipe.entity.RecipeTag;
import com.cookgenie.domain.recipe.entity.RecipeTagId;
import com.cookgenie.domain.recipe.entity.RecipeType;
import com.cookgenie.domain.recipe.entity.Tag;
import com.cookgenie.domain.recipe.external.ClaudeRecipeClient;
import com.cookgenie.domain.recipe.external.GeneratedRecipe;
import com.cookgenie.domain.recipe.repository.RecipeIngredientRepository;
import com.cookgenie.domain.recipe.repository.RecipeRepository;
import com.cookgenie.domain.recipe.repository.RecipeTagRepository;
import com.cookgenie.domain.recipe.repository.TagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 레시피 생성(AI)/조회/추천/삭제를 담당하는 서비스. */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 20;

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final TagRepository tagRepository;
    private final RecipeTagRepository recipeTagRepository;
    private final FridgeRepository fridgeRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final IngredientRepository ingredientRepository;
    private final ClaudeRecipeClient claudeRecipeClient;

    /** 냉장고에 있는 재료로 Claude에게 레시피를 생성시켜 저장한다(dataSource=AI). */
    @Transactional
    public RecipeResponse generateAiRecipe(Long fridgeId, AiRecipeGenerateRequest request) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }

        List<FridgeItem> items = fridgeItemRepository.findByFridgeId(fridgeId);
        if (items.isEmpty()) {
            throw new CustomException(ErrorMessage.FRIDGE_HAS_NO_ITEMS);
        }
        List<String> ingredientNames = items.stream()
                .map(item -> item.getIngredient().getName())
                .distinct()
                .toList();

        GeneratedRecipe generated = claudeRecipeClient.generate(ingredientNames, request.getNote())
                .orElseThrow(() -> new CustomException(ErrorMessage.RECIPE_GENERATION_FAILED));

        Recipe recipe = recipeRepository.save(
                Recipe.builder()
                        .title(generated.title())
                        .recipeType(RecipeType.AI)
                        .cookingType(generated.cookingType())
                        .instructions(generated.instructions() == null ? null : String.join("\n", generated.instructions()))
                        .servingSize(generated.servingSize())
                        .caloriesPerServing(generated.caloriesPerServing())
                        .carbohydrateG(generated.carbohydrateG())
                        .proteinG(generated.proteinG())
                        .fatG(generated.fatG())
                        .build()
        );

        List<RecipeIngredient> savedIngredients = saveGeneratedIngredients(recipe, generated);
        List<String> savedTagNames = saveGeneratedTags(recipe, generated);

        return new RecipeResponse(recipe, savedIngredients, savedTagNames);
    }

    /** 냉장고 재료와 겹치는 재료가 많은 순으로 기존 레시피를 추천한다. 하나도 안 겹치는 레시피는 제외한다. */
    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> getRecommendations(Long fridgeId, Integer limit) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }

        Set<Long> fridgeIngredientIds = fridgeItemRepository.findByFridgeId(fridgeId).stream()
                .map(item -> item.getIngredient().getId())
                .collect(Collectors.toSet());
        if (fridgeIngredientIds.isEmpty()) {
            return List.of();
        }

        List<Recipe> recipes = recipeRepository.findAll();
        List<Long> recipeIds = recipes.stream().map(Recipe::getId).toList();
        Map<Long, List<RecipeIngredient>> ingredientsByRecipeId = recipeIngredientRepository.findByRecipeIdIn(recipeIds)
                .stream()
                .collect(Collectors.groupingBy(ri -> ri.getRecipe().getId()));

        record Scored(Recipe recipe, int matched, int total) {
            double matchRate() {
                return total == 0 ? 0.0 : (double) matched / total;
            }
        }

        return recipes.stream()
                .map(recipe -> {
                    List<RecipeIngredient> recipeIngredients = ingredientsByRecipeId.getOrDefault(recipe.getId(), List.of());
                    int total = recipeIngredients.size();
                    int matched = (int) recipeIngredients.stream()
                            .filter(ri -> ri.getIngredient() != null && fridgeIngredientIds.contains(ri.getIngredient().getId()))
                            .count();
                    return new Scored(recipe, matched, total);
                })
                .filter(scored -> scored.matched() > 0)
                .sorted(Comparator.comparingDouble(Scored::matchRate).reversed()
                        .thenComparing(Comparator.comparingInt(Scored::matched).reversed()))
                .limit(limit != null && limit > 0 ? limit : DEFAULT_RECOMMENDATION_LIMIT)
                .map(scored -> new RecipeSummaryResponse(scored.recipe(), scored.matched(), scored.total()))
                .toList();
    }

    /** 레시피 전체 목록(최신순). */
    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> listRecipes() {
        return recipeRepository.findAll().stream()
                .sorted(Comparator.comparing(Recipe::getCreatedAt).reversed())
                .map(RecipeSummaryResponse::new)
                .toList();
    }

    /** 레시피 상세 조회. */
    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorMessage.RECIPE_NOT_FOUND));
        List<RecipeIngredient> ingredients = recipeIngredientRepository.findByRecipeId(recipeId);
        List<String> tagNames = recipeTagRepository.findByIdRecipeId(recipeId).stream()
                .map(rt -> rt.getTag().getName())
                .toList();
        return new RecipeResponse(recipe, ingredients, tagNames);
    }

    /** 레시피를 삭제한다(연결된 재료/태그도 함께 삭제). */
    @Transactional
    public void deleteRecipe(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorMessage.RECIPE_NOT_FOUND));
        recipeTagRepository.deleteAll(recipeTagRepository.findByIdRecipeId(recipeId));
        recipeIngredientRepository.deleteAll(recipeIngredientRepository.findByRecipeId(recipeId));
        recipeRepository.delete(recipe);
    }

    private List<RecipeIngredient> saveGeneratedIngredients(Recipe recipe, GeneratedRecipe generated) {
        if (generated.ingredients() == null) {
            return List.of();
        }
        return generated.ingredients().stream()
                .map(gi -> recipeIngredientRepository.save(
                        RecipeIngredient.builder()
                                .recipe(recipe)
                                .ingredient(matchIngredient(gi.name()))
                                .ingredientNameText(gi.name())
                                .quantityText(gi.quantityText())
                                .quantityValue(gi.quantityValue())
                                .unit(gi.unit())
                                .build()
                ))
                .toList();
    }

    private List<String> saveGeneratedTags(Recipe recipe, GeneratedRecipe generated) {
        if (generated.tags() == null) {
            return List.of();
        }
        return generated.tags().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> {
                    Tag tag = tagRepository.findByName(name)
                            .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
                    recipeTagRepository.save(
                            RecipeTag.builder()
                                    .id(new RecipeTagId(recipe.getId(), tag.getId()))
                                    .recipe(recipe)
                                    .tag(tag)
                                    .build()
                    );
                    return tag.getName();
                })
                .toList();
    }

    /** 이름으로 식재료 마스터와 매칭을 시도한다. 정확히 일치하는 게 없으면 부분 일치라도 찾고, 그래도 없으면 null(텍스트로만 표시). */
    private Ingredient matchIngredient(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<Ingredient> exact = ingredientRepository.findAllByName(name);
        if (!exact.isEmpty()) {
            return exact.get(0);
        }
        List<Ingredient> partial = ingredientRepository.findByNameContaining(name);
        return partial.isEmpty() ? null : partial.get(0);
    }
}
