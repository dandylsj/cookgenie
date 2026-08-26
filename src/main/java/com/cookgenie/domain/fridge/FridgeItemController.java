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

@RestController
@RequestMapping("/fridges/{fridgeId}/items")
@RequiredArgsConstructor
public class FridgeItemController {

    private final FridgeItemService fridgeItemService;

    @PostMapping
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> createItem(
            @PathVariable Long fridgeId, @Valid @RequestBody FridgeItemCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.createItem(fridgeId, request)));
    }

    @GetMapping
    public ResponseEntity<GlobalResponse<List<FridgeItemResponse>>> getItems(@PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getItems(fridgeId)));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> getItem(
            @PathVariable Long fridgeId, @PathVariable Long itemId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getItem(fridgeId, itemId)));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<GlobalResponse<FridgeItemResponse>> updateItem(
            @PathVariable Long fridgeId, @PathVariable Long itemId, @Valid @RequestBody FridgeItemUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.updateItem(fridgeId, itemId, request)));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long fridgeId, @PathVariable Long itemId) {
        fridgeItemService.deleteItem(fridgeId, itemId);
        return ResponseEntity.noContent().build();
    }
}
