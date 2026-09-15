package com.cookgenie.domain.recipe;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.recipe.dto.AiRecipeGenerateRequest;
import com.cookgenie.domain.recipe.dto.RecipeResponse;
import com.cookgenie.domain.recipe.dto.RecipeSummaryResponse;
import com.cookgenie.domain.recipe.dto.YoutubeVideoSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 특정 냉장고(fridgeId)를 기준으로 한 레시피 생성/추천 API. */
@Tag(name = "레시피(Recipe)", description = "냉장고 재료 기반 AI 레시피 생성/추천, 레시피 조회 API")
@RestController
@RequestMapping("/fridges/{fridgeId}/recipes")
@RequiredArgsConstructor
public class FridgeRecipeController {

    private final RecipeService recipeService;

    /** POST /fridges/{fridgeId}/recipes/generate - 냉장고 재료로 AI 레시피 생성 */
    @Operation(
            summary = "AI 레시피 생성",
            description = "레시피 하나를 생성해서 저장합니다(이후 추천/조회에도 활용됨). "
                    + "useFridgeIngredients가 true(기본값, 생략해도 true)면 냉장고에 있는 재료들을 Claude에게 전달해서 "
                    + "그 재료로 만들 수 있는 레시피를 생성합니다(냉장고에 재료가 없으면 400 에러). false면 냉장고 재료를 "
                    + "무시하고 note에만 맞는 레시피를 자유롭게 생성합니다."
    )
    @PostMapping("/generate")
    public ResponseEntity<GlobalResponse<RecipeResponse>> generate(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @RequestBody(required = false) AiRecipeGenerateRequest request) {
        AiRecipeGenerateRequest body = request != null ? request : new AiRecipeGenerateRequest();
        return ResponseEntity.ok(GlobalResponse.success(recipeService.generateAiRecipe(fridgeId, body)));
    }

    /** GET /fridges/{fridgeId}/recipes/recommendations - 재료 기반 레시피 추천 */
    @Operation(
            summary = "재료 기반 레시피 추천",
            description = "냉장고 재료와 겹치는 재료가 많은 순으로 기존 레시피를 추천합니다. 하나도 안 겹치면 목록에서 제외됩니다."
    )
    @GetMapping("/recommendations")
    public ResponseEntity<GlobalResponse<List<RecipeSummaryResponse>>> getRecommendations(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "최대 개수 (기본 20)") @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(GlobalResponse.success(recipeService.getRecommendations(fridgeId, limit)));
    }

    /** GET /fridges/{fridgeId}/recipes/youtube/search - 유튜브 레시피 검색 */
    @Operation(
            summary = "유튜브 레시피 검색",
            description = "keyword를 지정하지 않으면 냉장고 재료 이름으로 검색어를 만들어 유튜브에서 요리 영상을 찾습니다. "
                    + "검색 결과는 저장되지 않으며, 실제로 레시피로 가져오려면 POST /recipes/youtube/import를 호출해야 합니다."
    )
    @GetMapping("/youtube/search")
    public ResponseEntity<GlobalResponse<List<YoutubeVideoSummaryResponse>>> searchYoutubeRecipes(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "검색어 (없으면 냉장고 재료로 자동 구성)") @RequestParam(required = false) String keyword,
            @Parameter(description = "최대 개수 (기본 10)") @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(GlobalResponse.success(recipeService.searchYoutubeRecipes(fridgeId, keyword, limit)));
    }
}
