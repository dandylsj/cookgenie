package com.cookgenie.domain.fridge;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.fridge.dto.FridgeItemCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeItemResponse;
import com.cookgenie.domain.fridge.dto.FridgeItemUpdateRequest;
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
@RestController
@RequestMapping("/fridges/{fridgeId}/items")
@RequiredArgsConstructor
public class FridgeItemController {

    private final FridgeItemService fridgeItemService;

    /** POST /fridges/{fridgeId}/items - 재료 추가 */
    @PostMapping
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> createItem(
            @PathVariable Long fridgeId, @Valid @RequestBody FridgeItemCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.createItem(fridgeId, request)));
    }

    /** GET /fridges/{fridgeId}/items - 재료 목록 조회 */
    @GetMapping
    public ResponseEntity<GlobalResponse<List<FridgeItemResponse>>> getItems(@PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getItems(fridgeId)));
    }

    /** GET /fridges/{fridgeId}/items/{itemId} - 재료 단건 조회 */
    @GetMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> getItem(
            @PathVariable Long fridgeId, @PathVariable Long itemId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getItem(fridgeId, itemId)));
    }

    /** PUT /fridges/{fridgeId}/items/{itemId} - 재료 수정 (수량/단위/보관위치/날짜/메모) */
    @PutMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> updateItem(
            @PathVariable Long fridgeId, @PathVariable Long itemId, @Valid @RequestBody FridgeItemUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.updateItem(fridgeId, itemId, request)));
    }

    /** DELETE /fridges/{fridgeId}/items/{itemId} - 재료 삭제 */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long fridgeId, @PathVariable Long itemId) {
        fridgeItemService.deleteItem(fridgeId, itemId);
        return ResponseEntity.noContent().build();
    }
}
