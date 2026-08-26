package com.cookgenie.domain.fridge;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.dto.FridgeItemCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeItemResponse;
import com.cookgenie.domain.fridge.dto.FridgeItemUpdateRequest;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeItem;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FridgeItemService {

    private final FridgeItemRepository fridgeItemRepository;
    private final FridgeRepository fridgeRepository;
    private final IngredientRepository ingredientRepository;

    @Transactional
    public FridgeItemResponse createItem(Long fridgeId, FridgeItemCreateRequest request) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new CustomException(ErrorMessage.FRIDGE_NOT_FOUND));
        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new CustomException(ErrorMessage.INGREDIENT_NOT_FOUND));

        FridgeItem item = FridgeItem.builder()
                .fridge(fridge)
                .ingredient(ingredient)
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .storageLocation(request.getStorageLocation())
                .purchasedAt(request.getPurchasedAt())
                .expiryDate(request.getExpiryDate())
                .memo(request.getMemo())
                .build();

        return new FridgeItemResponse(fridgeItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<FridgeItemResponse> getItems(Long fridgeId) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }
        return fridgeItemRepository.findByFridgeId(fridgeId).stream()
                .map(FridgeItemResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public FridgeItemResponse getItem(Long fridgeId, Long itemId) {
        return new FridgeItemResponse(getItemInFridge(fridgeId, itemId));
    }

    @Transactional
    public FridgeItemResponse updateItem(Long fridgeId, Long itemId, FridgeItemUpdateRequest request) {
        FridgeItem item = getItemInFridge(fridgeId, itemId);
        item.update(
                request.getQuantity(),
                request.getUnit(),
                request.getStorageLocation(),
                request.getPurchasedAt(),
                request.getExpiryDate(),
                request.getMemo()
        );
        return new FridgeItemResponse(item);
    }

    @Transactional
    public void deleteItem(Long fridgeId, Long itemId) {
        FridgeItem item = getItemInFridge(fridgeId, itemId);
        fridgeItemRepository.delete(item);
    }

    private FridgeItem getItemInFridge(Long fridgeId, Long itemId) {
        FridgeItem item = fridgeItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorMessage.FRIDGE_ITEM_NOT_FOUND));
        if (!item.getFridge().getId().equals(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_ITEM_NOT_FOUND);
        }
        return item;
    }
}
