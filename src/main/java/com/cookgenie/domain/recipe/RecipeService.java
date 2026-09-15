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
import com.cookgenie.domain.recipe.dto.YoutubeVideoSummaryResponse;
import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.recipe.entity.RecipeIngredient;
import com.cookgenie.domain.recipe.entity.RecipeTag;
import com.cookgenie.domain.recipe.entity.RecipeTagId;
import com.cookgenie.domain.recipe.entity.RecipeType;
import com.cookgenie.domain.recipe.entity.Tag;
import com.cookgenie.domain.recipe.external.ClaudeRecipeClient;
import com.cookgenie.domain.recipe.external.GeneratedRecipe;
import com.cookgenie.domain.recipe.external.YoutubeSearchClient;
import com.cookgenie.domain.recipe.external.YoutubeVideo;
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

/** 레시피 생성(AI)/유튜브 연동/조회/추천/삭제를 담당하는 서비스. */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 20;
    private static final int DEFAULT_YOUTUBE_SEARCH_LIMIT = 10;
    private static final int YOUTUBE_QUERY_INGREDIENT_COUNT = 3;

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final TagRepository tagRepository;
    private final RecipeTagRepository recipeTagRepository;
    private final FridgeRepository fridgeRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final IngredientRepository ingredientRepository;
    private final ClaudeRecipeClient claudeRecipeClient;
    private final YoutubeSearchClient youtubeSearchClient;

    /**
     * 레시피를 Claude에게 생성시켜 저장한다(dataSource=AI).
     * useFridgeIngredients=true(기본)면 냉장고 재료를 기준으로, false면 냉장고 재료는 무시하고 note 요청대로만 자유롭게 생성한다.
     */
    @Transactional
    public RecipeResponse generateAiRecipe(Long fridgeId, AiRecipeGenerateRequest request) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }

        List<String> ingredientNames = List.of();
        if (request.isUseFridgeIngredients()) {
            List<FridgeItem> items = fridgeItemRepository.findByFridgeId(fridgeId);
            if (items.isEmpty()) {
                throw new CustomException(ErrorMessage.FRIDGE_HAS_NO_ITEMS);
            }
            ingredientNames = items.stream()
                    .map(item -> item.getIngredient().getName())
                    .distinct()
                    .toList();
        } else if (request.getNote() == null || request.getNote().isBlank()) {
            throw new CustomException(ErrorMessage.RECIPE_NOTE_REQUIRED);
        }

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
        return toResponse(recipe);
    }

    /** 냉장고 재료(또는 keyword)를 기반으로 유튜브 요리 영상을 검색한다. 저장하지 않고 미리보기 목록만 보여준다. */
    @Transactional(readOnly = true)
    public List<YoutubeVideoSummaryResponse> searchYoutubeRecipes(Long fridgeId, String keyword, Integer limit) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }

        String query = keyword != null && !keyword.isBlank() ? keyword : buildQueryFromFridgeItems(fridgeId);
        int maxResults = limit != null && limit > 0 ? limit : DEFAULT_YOUTUBE_SEARCH_LIMIT;

        return youtubeSearchClient.search(query + " 레시피", maxResults).stream()
                .map(YoutubeVideoSummaryResponse::new)
                .toList();
    }

    /**
     * 유튜브 영상을 가져와 레시피로 저장한다(recipeType=YOUTUBE). 영상 제목/설명을 Claude에게 전달해 재료/조리법을 추출한다.
     * 이미 가져온 영상이면 다시 호출하지 않고 기존 레시피를 그대로 반환한다.
     */
    @Transactional
    public RecipeResponse importYoutubeRecipe(String videoId) {
        String sourceUrl = "https://www.youtube.com/watch?v=" + videoId;
        return recipeRepository.findBySourceUrl(sourceUrl)
                .map(this::toResponse)
                .orElseGet(() -> {
                    YoutubeVideo video = youtubeSearchClient.getVideoDetail(videoId)
                            .orElseThrow(() -> new CustomException(ErrorMessage.YOUTUBE_VIDEO_NOT_FOUND));

                    GeneratedRecipe generated = claudeRecipeClient.parseFromYoutube(video.title(), video.description())
                            .orElseThrow(() -> new CustomException(ErrorMessage.RECIPE_GENERATION_FAILED));

                    Recipe recipe = recipeRepository.save(
                            Recipe.builder()
                                    .title(generated.title())
                                    .recipeType(RecipeType.YOUTUBE)
                                    .cookingType(generated.cookingType())
                                    .instructions(generated.instructions() == null
                                            ? null : String.join("\n", generated.instructions()))
                                    .sourceUrl(sourceUrl)
                                    .authorNickname(video.channelTitle())
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
                });
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

    private RecipeResponse toResponse(Recipe recipe) {
        List<RecipeIngredient> ingredients = recipeIngredientRepository.findByRecipeId(recipe.getId());
        List<String> tagNames = recipeTagRepository.findByIdRecipeId(recipe.getId()).stream()
                .map(rt -> rt.getTag().getName())
                .toList();
        return new RecipeResponse(recipe, ingredients, tagNames);
    }

    /** 냉장고 재료 이름 중 일부로 유튜브 검색어를 만든다. 재료가 없으면 예외. */
    private String buildQueryFromFridgeItems(Long fridgeId) {
        List<String> ingredientNames = fridgeItemRepository.findByFridgeId(fridgeId).stream()
                .map(item -> item.getIngredient().getName())
                .distinct()
                .limit(YOUTUBE_QUERY_INGREDIENT_COUNT)
                .toList();
        if (ingredientNames.isEmpty()) {
            throw new CustomException(ErrorMessage.FRIDGE_HAS_NO_ITEMS);
        }
        return String.join(" ", ingredientNames);
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
