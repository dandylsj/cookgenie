package com.cookgenie.domain.ingredient;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.dto.RawMaterialSyncResponse;
import com.cookgenie.domain.ingredient.dto.IngredientUpdateRequest;
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

    /** POST /ingredients - 목록에 없는 새 식재료 등록 (사용자 직접 입력) */
    @Operation(summary = "식재료 등록", description = "검색 결과에 없는 식재료를 사용자가 직접 등록합니다.")
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

    /** DELETE /ingredients/{id} - 식재료 삭제 (냉장고에 등록되어 있으면 삭제 불가) */
    @Operation(summary = "식재료 삭제", description = "식재료를 삭제합니다. 이미 어떤 냉장고에 등록되어 있으면 삭제할 수 없습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIngredient(@Parameter(description = "식재료 ID") @PathVariable Long id) {
        ingredientService.deleteIngredient(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /ingredients/sync-raw-materials - 농촌진흥청 원재료 영양성분 공공데이터 전체 동기화 */
    @Operation(
            summary = "원재료 영양정보 동기화",
            description = "농촌진흥청 \"전국통합식품영양성분정보(원재료성식품)\" 공공데이터 전체를 가져와 식재료/영양정보를 채우거나 갱신합니다. "
                    + "매달 자동으로도 실행되지만, 즉시 반영이 필요할 때 수동으로 호출할 수 있습니다."
    )
    @PostMapping("/sync-raw-materials")
    public ResponseEntity<GlobalResponse<RawMaterialSyncResponse>> syncRawMaterials() {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.syncRawMaterialsFromMfds()));
    }
}
