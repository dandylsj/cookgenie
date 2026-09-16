package com.cookgenie.domain.shopping;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.shopping.dto.ShoppingItemAddRequest;
import com.cookgenie.domain.shopping.dto.ShoppingItemResponse;
import com.cookgenie.domain.shopping.entity.ShoppingItem;
import com.cookgenie.domain.shopping.repository.ShoppingItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 냉장고별 장보기 리스트(ShoppingItem) CRUD를 담당하는 서비스. */
@Service
@RequiredArgsConstructor
public class ShoppingItemService {

    private final ShoppingItemRepository shoppingItemRepository;
    private final FridgeRepository fridgeRepository;

    /** 장보기 항목 추가. */
    @Transactional
    public ShoppingItemResponse addItem(Long fridgeId, ShoppingItemAddRequest request) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new CustomException(ErrorMessage.FRIDGE_NOT_FOUND));

        ShoppingItem item = shoppingItemRepository.save(
                ShoppingItem.builder()
                        .fridge(fridge)
                        .name(request.getName().trim())
                        .build()
        );

        return new ShoppingItemResponse(item);
    }

    /** 장보기 목록 조회. 미완료 항목을 먼저, 그다음 최신순으로 보여준다. */
    @Transactional(readOnly = true)
    public List<ShoppingItemResponse> getItems(Long fridgeId) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }
        return shoppingItemRepository.findByFridgeIdOrderByCheckedAscCreatedAtDesc(fridgeId).stream()
                .map(ShoppingItemResponse::new)
                .toList();
    }

    /** 장보기 항목 완료/미완료 체크. */
    @Transactional
    public ShoppingItemResponse updateChecked(Long fridgeId, Long itemId, boolean checked) {
        ShoppingItem item = getItemInFridge(fridgeId, itemId);
        item.updateChecked(checked);
        return new ShoppingItemResponse(item);
    }

    /** 장보기 항목 삭제. */
    @Transactional
    public void deleteItem(Long fridgeId, Long itemId) {
        ShoppingItem item = getItemInFridge(fridgeId, itemId);
        shoppingItemRepository.delete(item);
    }

    /** itemId로 항목을 찾고, 그 항목이 실제로 fridgeId 소속인지까지 확인한다 (다른 냉장고 항목 접근 방지). */
    private ShoppingItem getItemInFridge(Long fridgeId, Long itemId) {
        ShoppingItem item = shoppingItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorMessage.SHOPPING_ITEM_NOT_FOUND));
        if (!item.getFridge().getId().equals(fridgeId)) {
            throw new CustomException(ErrorMessage.SHOPPING_ITEM_NOT_FOUND);
        }
        return item;
    }
}
