package com.cookgenie.domain.shopping;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.shopping.dto.ShoppingItemAddRequest;
import com.cookgenie.domain.shopping.dto.ShoppingItemCheckRequest;
import com.cookgenie.domain.shopping.dto.ShoppingItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 특정 냉장고(fridgeId)의 장보기 리스트(ShoppingItem) CRUD API. */
@Tag(name = "장보기(Shopping)", description = "냉장고별 장보기 리스트 추가/조회/체크/삭제 API")
@RestController
@RequestMapping("/fridges/{fridgeId}/shopping-items")
@RequiredArgsConstructor
public class ShoppingItemController {

    private final ShoppingItemService shoppingItemService;

    /** POST /fridges/{fridgeId}/shopping-items - 장보기 항목 추가 */
    @Operation(summary = "장보기 항목 추가", description = "냉장고의 장보기 리스트에 항목을 하나 추가합니다.")
    @PostMapping
    public ResponseEntity<GlobalResponse<ShoppingItemResponse>> addItem(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Valid @RequestBody ShoppingItemAddRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(shoppingItemService.addItem(fridgeId, request)));
    }

    /** GET /fridges/{fridgeId}/shopping-items - 장보기 목록 조회 */
    @Operation(summary = "장보기 목록 조회", description = "냉장고의 장보기 리스트 전체를 조회합니다. 미완료 항목이 먼저, 그다음 최신순입니다.")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<ShoppingItemResponse>>> getItems(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(shoppingItemService.getItems(fridgeId)));
    }

    /** PATCH /fridges/{fridgeId}/shopping-items/{itemId} - 완료/미완료 체크 */
    @Operation(summary = "장보기 항목 체크", description = "장보기 항목을 완료/미완료로 표시합니다.")
    @PatchMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<ShoppingItemResponse>> updateChecked(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "장보기 항목 ID") @PathVariable Long itemId,
            @Valid @RequestBody ShoppingItemCheckRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(
                shoppingItemService.updateChecked(fridgeId, itemId, request.getChecked())));
    }

    /** DELETE /fridges/{fridgeId}/shopping-items/{itemId} - 장보기 항목 삭제 */
    @Operation(summary = "장보기 항목 삭제", description = "장보기 리스트에서 항목을 삭제합니다.")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "장보기 항목 ID") @PathVariable Long itemId) {
        shoppingItemService.deleteItem(fridgeId, itemId);
        return ResponseEntity.noContent().build();
    }
}
