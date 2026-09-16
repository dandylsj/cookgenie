package com.cookgenie.domain.ingredient;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.dto.IngredientSuggestionResponse;
import com.cookgenie.domain.ingredient.dto.IngredientUpdateRequest;
import com.cookgenie.domain.ingredient.dto.NutritionUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 식재료 마스터(Ingredient) 검색/등록 API. 냉장고에 재료를 추가할 때 ingredientId를 얻기 위해 사용한다. */
@Tag(name = "식재료(Ingredient)", description = "식재료 마스터 검색/등록, 카테고리 조회 API")
@RestController
@RequestMapping("/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    /** GET /ingredients?keyword= - 이름으로 식재료 검색 (keyword 없으면 전체 목록) */
    @Operation(summary = "식재료 검색", description = "이름에 keyword가 포함된 식재료를 검색합니다. keyword가 없으면 전체 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<IngredientResponse>>> searchIngredients(
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.searchIngredients(keyword)));
    }

    /** GET /ingredients/categories - 식재료 카테고리 전체 목록 조회 */
    @Operation(summary = "카테고리 목록 조회", description = "식재료 카테고리 전체 목록을 조회합니다.")
    @GetMapping("/categories")
    public ResponseEntity<GlobalResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.getCategories()));
    }

    /** GET /ingredients/categories/{categoryId}/suggestions - 카테고리별 추천 재료 이름 목록 조회 */
    @Operation(
            summary = "카테고리별 추천 재료 조회",
            description = "재료 추가 화면에서 카테고리를 고르면 보여줄, 자주 쓰는 재료 이름 목록을 반환합니다. "
                    + "DB/AI 호출 없는 정적 목록이며, 사용자가 그중 하나를 고르면 그 이름 그대로 POST /ingredients를 "
                    + "호출하면 됩니다(이미 등록된 이름이면 즉시 재사용, 처음이면 Claude가 영양정보를 추정)."
    )
    @GetMapping("/categories/{categoryId}/suggestions")
    public ResponseEntity<GlobalResponse<List<IngredientSuggestionResponse>>> getSuggestions(
            @Parameter(description = "카테고리 ID") @PathVariable Long categoryId) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.getSuggestions(categoryId)));
    }

    /** POST /ingredients - 목록에 없는 새 식재료 등록 (같은 이름이 있으면 재사용) */
    @Operation(
            summary = "식재료 등록",
            description = "검색 결과에 없는 식재료를 등록합니다. 같은 이름의 식재료가 이미 있으면 그대로 재사용합니다. "
                    + "완전히 새 이름일 때: calories/carbohydrateG/proteinG/fatG를 직접 주면 그 값을 그대로 저장하고, "
                    + "안 주고 autoEstimateNutrition=true면 Claude가 100g 기준 영양정보를 추정해서 저장합니다. "
                    + "둘 다 안 하면(기본값) 영양정보 없이 등록되며, 나중에 PUT .../nutrition(직접 입력) 또는 "
                    + "POST .../nutrition/estimate(AI 추정)로 채울 수 있습니다. "
                    + "(여러 재료를 한 번에 등록할 때 매번 Claude를 호출하면 토큰이 많이 들어서 기본은 호출하지 않습니다.)"
    )
    @PostMapping
    public ResponseEntity<GlobalResponse<IngredientResponse>> createIngredient(
            @Valid @RequestBody IngredientCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.createIngredient(request)));
    }

    /** PUT /ingredients/{id} - 식재료 이름/카테고리/기본 단위 수정 */
    @Operation(summary = "식재료 수정", description = "잘못 등록한 식재료의 이름, 카테고리, 기본 단위를 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<GlobalResponse<IngredientResponse>> updateIngredient(
            @Parameter(description = "식재료 ID") @PathVariable Long id,
            @Valid @RequestBody IngredientUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.updateIngredient(id, request)));
    }

    /** PUT /ingredients/{id}/nutrition - 영양정보 직접 입력/수정 */
    @Operation(
            summary = "영양정보 직접 입력/수정",
            description = "식재료의 100g(또는 ml) 기준 영양정보를 사용자가 직접 입력하거나 수정합니다. "
                    + "영양정보가 없는 재료에 처음 채워 넣을 때, 또는 AI 추정값이 부정확할 때 사용합니다. "
                    + "저장 후 dataSource는 USER_INPUT, isVerified는 true가 됩니다."
    )
    @PutMapping("/{id}/nutrition")
    public ResponseEntity<GlobalResponse<IngredientResponse>> updateNutrition(
            @Parameter(description = "식재료 ID") @PathVariable Long id,
            @RequestBody NutritionUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.updateNutrition(id, request)));
    }

    /** POST /ingredients/{id}/nutrition/estimate - Claude로 영양정보 추정(나중에 채우기) */
    @Operation(
            summary = "영양정보 AI 추정",
            description = "등록 시점에 영양정보를 채우지 않은 식재료(또는 다시 추정받고 싶은 식재료)에 대해 "
                    + "그 시점에 Claude로 100g 기준 영양정보 추정을 요청합니다. 저장 후 dataSource는 LLM_ESTIMATED가 됩니다."
    )
    @PostMapping("/{id}/nutrition/estimate")
    public ResponseEntity<GlobalResponse<IngredientResponse>> estimateNutrition(
            @Parameter(description = "식재료 ID") @PathVariable Long id) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.estimateNutrition(id)));
    }

    /** DELETE /ingredients/{id} - 식재료 삭제 (냉장고에 등록되어 있으면 삭제 불가) */
    @Operation(summary = "식재료 삭제", description = "식재료를 삭제합니다. 이미 어떤 냉장고에 등록되어 있으면 삭제할 수 없습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIngredient(@Parameter(description = "식재료 ID") @PathVariable Long id) {
        ingredientService.deleteIngredient(id);
        return ResponseEntity.noContent().build();
    }
}
