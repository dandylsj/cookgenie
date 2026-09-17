package com.cookgenie.domain.ingredient;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.dto.IngredientSuggestionResponse;
import com.cookgenie.domain.ingredient.dto.IngredientUpdateRequest;
import com.cookgenie.domain.ingredient.dto.NutritionUpdateRequest;
import com.cookgenie.domain.ingredient.dto.OfficialFoodCandidateResponse;
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
    private final OfficialNutritionSyncService officialNutritionSyncService;
    private final OfficialProcessedFoodSyncService officialProcessedFoodSyncService;

    /** GET /ingredients?keyword=&categoryId= - 이름/카테고리로 식재료 검색 (둘 다 없으면 전체 목록) */
    @Operation(
            summary = "식재료 검색",
            description = "이름에 keyword가 포함된 식재료를 검색합니다. categoryId를 함께 주면 그 카테고리 안에서만 "
                    + "찾습니다(재료 추가 화면에서 카테고리를 고른 뒤 그 안의 기존 재료를 검색해서 바로 고를 때 사용)."
    )
    @GetMapping
    public ResponseEntity<GlobalResponse<List<IngredientResponse>>> searchIngredients(
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "카테고리 ID") @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.searchIngredients(keyword, categoryId)));
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

    /** GET /ingredients/official-search?keyword=&limit= - 식약처 가공식품 공공데이터에서 이름으로 후보 검색 */
    @Operation(
            summary = "가공식품 공공데이터 검색",
            description = "식약처 가공식품 공공데이터에서 keyword(부분 일치)로 후보를 검색합니다. \"실온\"으로 검색해서 "
                    + "쭉 보다가 \"닭\"을 덧붙여 좁혀가는 식으로 쓸 수 있습니다. foodNm에 브랜드명이 안 들어있는 경우가 "
                    + "많아서 mfrNm(제조사)도 같이 내려주니 화면에 같이 보여주세요. 사용자가 후보 하나를 고르면 그 "
                    + "값(calories/carbohydrateG/proteinG/fatG, 전부 100g/100ml 기준으로 정규화됨)을 그대로 "
                    + "POST /ingredients에 직접 입력값으로 넘기면 됩니다."
    )
    @GetMapping("/official-search")
    public ResponseEntity<GlobalResponse<List<OfficialFoodCandidateResponse>>> searchOfficialFoods(
            @Parameter(description = "검색 키워드") @RequestParam String keyword,
            @Parameter(description = "최대 개수 (기본 20)") @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(GlobalResponse.success(ingredientService.searchOfficialFoods(keyword, limit)));
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

    /** POST /ingredients/sync-official-nutrition - 공공데이터포털 원재료성식품 CSV 동기화 */
    @Operation(
            summary = "식약처 공식 영양정보 동기화",
            description = "공공데이터포털 원재료성식품 표준데이터(농촌진흥청 농산물 + 해양수산부 수산물, 앱에 "
                    + "번들된 CSV)를 대표식품코드 기준으로 묶어서(품종/조리상태 차이는 대표값 하나로 합침, "
                    + "가능하면 '생것' 우선) 아직 등록되지 않은 이름만 새 식재료로 등록합니다(dataSource=OFFICIAL_DB). "
                    + "이미 있는 재료는 덮어쓰지 않고 건너뜁니다."
    )
    @PostMapping("/sync-official-nutrition")
    public ResponseEntity<GlobalResponse<OfficialNutritionSyncService.SyncResult>> syncOfficialNutrition() {
        return ResponseEntity.ok(GlobalResponse.success(officialNutritionSyncService.syncFromCsv()));
    }

    /** POST /ingredients/sync-official-processed-foods - 공공데이터포털 가공식품 CSV 동기화 */
    @Operation(
            summary = "식약처 공식 가공식품 영양정보 동기화",
            description = "공공데이터포털 가공식품 표준데이터(브랜드별 개별 상품 31만여 건을 대표식품코드 "
                    + "기준으로 미리 압축한 270여 건, 앱에 번들된 CSV)를 아직 등록되지 않은 이름만 새 식재료로 "
                    + "등록합니다(dataSource=OFFICIAL_DB, ingredientType=PROCESSED). 마요네즈/간장/식용유 같은 "
                    + "조미료·가공품이 대상이며, 원재료성식품 동기화와 완전히 별개로 동작합니다. "
                    + "이미 있는 재료는 덮어쓰지 않고 건너뜁니다."
    )
    @PostMapping("/sync-official-processed-foods")
    public ResponseEntity<GlobalResponse<OfficialNutritionSyncService.SyncResult>> syncOfficialProcessedFoods() {
        return ResponseEntity.ok(GlobalResponse.success(officialNutritionSyncService.syncProcessedFoodsFromCsv()));
    }

    /** POST /ingredients/official-foods/sync - 가공식품 공공데이터 전체(약 59만 건)를 로컬 DB로 복사 */
    @Operation(
            summary = "가공식품 공공데이터 전체 동기화",
            description = "식약처 가공식품 공공데이터 API 전체(약 59만 건)를 foodNm 필터 없이 페이지 단위로 전부 "
                    + "가져와 로컬 테이블(official_processed_foods)에 복사합니다. GET /ingredients/official-search가 "
                    + "이 테이블에서 부분(포함) 검색을 하므로, 이 동기화가 끝나야 삼성헬스 스타일로 몇 글자만 쳐도 "
                    + "후보가 뜹니다(정부 API 자체는 foodNm 완전 일치만 지원해서 부분검색이 안 됨). "
                    + "이미 데이터가 있으면 아무것도 하지 않고 현재 건수를 알려주며, force=true면 기존 데이터는 "
                    + "그대로 두고 전체를 다시 훑어 누락된 항목만 추가로 저장합니다(정부 API 페이지네이션이 "
                    + "안정적이지 않아 한 번에 다 안 채워질 수 있어서, 다 채워질 때까지 여러 번 force=true로 "
                    + "재호출하면 됩니다 - 이미 저장된 항목은 건드리지 않아 안전합니다). "
                    + "백그라운드로 실행되며 몇 분 정도 걸릴 수 있습니다(순차적으로 약 591번 호출)."
    )
    @PostMapping("/official-foods/sync")
    public ResponseEntity<GlobalResponse<OfficialProcessedFoodSyncService.SyncTriggerResult>> syncOfficialProcessedFoodMirror(
            @Parameter(description = "이미 데이터가 있어도 전체를 다시 훑어서 누락된 항목을 추가로 채울지 여부") @RequestParam(required = false, defaultValue = "false") boolean force) {
        OfficialProcessedFoodSyncService.SyncTriggerResult result = officialProcessedFoodSyncService.prepareSync(force);
        if (result.started()) {
            officialProcessedFoodSyncService.runSync();
        }
        return ResponseEntity.ok(GlobalResponse.success(result));
    }

    /** DELETE /ingredients/{id} - 식재료 삭제 (냉장고에 등록되어 있으면 삭제 불가) */
    @Operation(summary = "식재료 삭제", description = "식재료를 삭제합니다. 이미 어떤 냉장고에 등록되어 있으면 삭제할 수 없습니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIngredient(@Parameter(description = "식재료 ID") @PathVariable Long id) {
        ingredientService.deleteIngredient(id);
        return ResponseEntity.noContent().build();
    }
}
