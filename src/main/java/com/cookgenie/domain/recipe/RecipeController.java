package com.cookgenie.domain.recipe;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.recipe.dto.RecipeResponse;
import com.cookgenie.domain.recipe.dto.RecipeSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 레시피 목록/상세 조회, 삭제 API. */
@Tag(name = "레시피(Recipe)", description = "냉장고 재료 기반 AI 레시피 생성/추천, 레시피 조회 API")
@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    /** GET /recipes - 전체 레시피 목록(최신순) */
    @Operation(summary = "레시피 목록 조회", description = "등록된 레시피 전체 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<RecipeSummaryResponse>>> listRecipes() {
        return ResponseEntity.ok(GlobalResponse.success(recipeService.listRecipes()));
    }

    /** GET /recipes/{id} - 레시피 상세 조회 */
    @Operation(summary = "레시피 상세 조회", description = "재료 목록, 조리 순서, 태그를 포함한 레시피 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<GlobalResponse<RecipeResponse>> getRecipe(
            @Parameter(description = "레시피 ID") @PathVariable Long id) {
        return ResponseEntity.ok(GlobalResponse.success(recipeService.getRecipe(id)));
    }

    /** DELETE /recipes/{id} - 레시피 삭제 */
    @Operation(summary = "레시피 삭제", description = "레시피와 연결된 재료/태그를 함께 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@Parameter(description = "레시피 ID") @PathVariable Long id) {
        recipeService.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }
}
