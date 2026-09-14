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
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 냉장고에 등록된 재료(FridgeItem) CRUD를 담당하는 서비스.
 * 냉장고 자체(생성/조회/삭제)는 {@link FridgeService} 참고.
 */
@Service
@RequiredArgsConstructor
public class FridgeItemService {

    private final FridgeItemRepository fridgeItemRepository;
    private final FridgeRepository fridgeRepository;
    private final IngredientRepository ingredientRepository;
    private final NutritionInfoRepository nutritionInfoRepository;

    /** 냉장고에 재료 추가. ingredientId가 식재료 마스터(Ingredient)에 존재해야 한다. */
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

        return toResponse(fridgeItemRepository.save(item));
    }

    /** 특정 냉장고에 등록된 재료 전체 목록 조회. */
    @Transactional(readOnly = true)
    public List<FridgeItemResponse> getItems(Long fridgeId) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }
        return fridgeItemRepository.findByFridgeId(fridgeId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 재료 단건 조회. */
    @Transactional(readOnly = true)
    public FridgeItemResponse getItem(Long fridgeId, Long itemId) {
        return toResponse(getItemInFridge(fridgeId, itemId));
    }

    /** 재료 수정. ingredientId(어떤 식재료인지)는 바꿀 수 없고 수량/단위/보관위치/날짜/메모만 갱신한다. */
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
        return toResponse(item);
    }

    /** 재료 삭제. */
    @Transactional
    public void deleteItem(Long fridgeId, Long itemId) {
        FridgeItem item = getItemInFridge(fridgeId, itemId);
        fridgeItemRepository.delete(item);
    }

    /** itemId로 재료를 찾고, 그 재료가 실제로 fridgeId 소속인지까지 확인한다 (다른 냉장고 재료 접근 방지). */
    private FridgeItem getItemInFridge(Long fridgeId, Long itemId) {
        FridgeItem item = fridgeItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorMessage.FRIDGE_ITEM_NOT_FOUND));
        if (!item.getFridge().getId().equals(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_ITEM_NOT_FOUND);
        }
        return item;
    }

    /** 재료의 NutritionInfo를 찾아 수량 기준으로 환산한 응답을 만든다. 영양정보가 없으면 탄단지 필드는 null로 내려간다. */
    private FridgeItemResponse toResponse(FridgeItem item) {
        NutritionInfo nutritionInfo = nutritionInfoRepository.findByIngredientId(item.getIngredient().getId())
                .orElse(null);
        return new FridgeItemResponse(item, nutritionInfo);
    }
}
