package com.cookgenie.domain.fridge;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.fridge.dto.FridgeItemCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeItemResponse;
import com.cookgenie.domain.fridge.dto.FridgeItemUpdateRequest;
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
import org.springframework.web.bind.annotation.RestController;

/** 특정 냉장고(fridgeId)에 등록된 재료(FridgeItem)의 CRUD API. */
@Tag(name = "냉장고 재료(FridgeItem)", description = "냉장고에 등록된 식재료의 추가/조회/수정/삭제 API")
@RestController
@RequestMapping("/fridges/{fridgeId}/items")
@RequiredArgsConstructor
public class FridgeItemController {

    private final FridgeItemService fridgeItemService;

    /** POST /fridges/{fridgeId}/items - 재료 추가 */
    @Operation(summary = "재료 추가", description = "냉장고에 새 식재료를 등록합니다.")
    @PostMapping
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> createItem(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Valid @RequestBody FridgeItemCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.createItem(fridgeId, request)));
    }

    /** GET /fridges/{fridgeId}/items - 재료 목록 조회 */
    @Operation(summary = "재료 목록 조회", description = "냉장고에 등록된 재료 전체 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<FridgeItemResponse>>> getItems(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getItems(fridgeId)));
    }

    /** GET /fridges/{fridgeId}/items/{itemId} - 재료 단건 조회 */
    @Operation(summary = "재료 단건 조회", description = "재료 하나의 상세 정보를 조회합니다.")
    @GetMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> getItem(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "재료 ID") @PathVariable Long itemId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getItem(fridgeId, itemId)));
    }

    /** PUT /fridges/{fridgeId}/items/{itemId} - 재료 수정 (수량/단위/보관위치/날짜/메모) */
    @Operation(summary = "재료 수정", description = "재료의 수량/단위/보관위치/구매일/유통기한/메모를 수정합니다. 어떤 식재료인지(ingredientId)는 변경할 수 없습니다.")
    @PutMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> updateItem(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "재료 ID") @PathVariable Long itemId,
            @Valid @RequestBody FridgeItemUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.updateItem(fridgeId, itemId, request)));
    }

    /** DELETE /fridges/{fridgeId}/items/{itemId} - 재료 삭제 */
    @Operation(summary = "재료 삭제", description = "냉장고에서 재료를 삭제합니다.")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "재료 ID") @PathVariable Long itemId) {
        fridgeItemService.deleteItem(fridgeId, itemId);
        return ResponseEntity.noContent().build();
    }
}
