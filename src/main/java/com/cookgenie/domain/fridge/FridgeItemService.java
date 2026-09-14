package com.cookgenie.domain.fridge;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.dto.CategoryDistribution;
import com.cookgenie.domain.fridge.dto.DailyActivityCount;
import com.cookgenie.domain.fridge.dto.FridgeItemCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeItemResponse;
import com.cookgenie.domain.fridge.dto.FridgeItemUpdateRequest;
import com.cookgenie.domain.fridge.dto.FridgeStatisticsResponse;
import com.cookgenie.domain.fridge.dto.StorageLocationDistribution;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeItem;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    private static final int EXPIRING_SOON_DAYS = 3;
    private static final int EXPIRY_ATTENTION_LIMIT = 5;
    private static final int LONG_NEGLECTED_LIMIT = 5;
    private static final int ACTIVITY_HEATMAP_DAYS = 150;
    private static final String UNCATEGORIZED_LABEL = "미분류";

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

    /**
     * 냉장고 재료 현황 통계. 소비기한 임박/지남 개수, 카테고리·보관위치 분포, 주의가 필요한 재료(소비기한 임박·지남),
     * 오래 방치된 재료(구매일 오래된 순), 최근 {@value ACTIVITY_HEATMAP_DAYS}일 등록 활동 히트맵을 함께 내려준다.
     */
    @Transactional(readOnly = true)
    public FridgeStatisticsResponse getStatistics(Long fridgeId) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }

        List<FridgeItem> items = fridgeItemRepository.findByFridgeId(fridgeId);
        LocalDate today = LocalDate.now();
        LocalDate expiringSoonThreshold = today.plusDays(EXPIRING_SOON_DAYS);

        long expiredCount = items.stream()
                .filter(item -> item.getExpiryDate() != null && item.getExpiryDate().isBefore(today))
                .count();
        long expiringSoonCount = items.stream()
                .filter(item -> item.getExpiryDate() != null
                        && !item.getExpiryDate().isBefore(today)
                        && !item.getExpiryDate().isAfter(expiringSoonThreshold))
                .count();

        List<CategoryDistribution> categoryDistribution = items.stream()
                .collect(Collectors.groupingBy(this::categoryNameOf, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new CategoryDistribution(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(CategoryDistribution::count).reversed())
                .toList();

        List<StorageLocationDistribution> storageLocationDistribution = items.stream()
                .collect(Collectors.groupingBy(FridgeItem::getStorageLocation, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new StorageLocationDistribution(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(StorageLocationDistribution::count).reversed())
                .toList();

        List<FridgeItemResponse> expiryAttentionItems = items.stream()
                .filter(item -> item.getExpiryDate() != null && !item.getExpiryDate().isAfter(expiringSoonThreshold))
                .sorted(Comparator.comparing(FridgeItem::getExpiryDate))
                .limit(EXPIRY_ATTENTION_LIMIT)
                .map(this::toResponse)
                .toList();

        List<FridgeItemResponse> longNeglectedItems = items.stream()
                .filter(item -> item.getPurchasedAt() != null)
                .sorted(Comparator.comparing(FridgeItem::getPurchasedAt))
                .limit(LONG_NEGLECTED_LIMIT)
                .map(this::toResponse)
                .toList();

        List<DailyActivityCount> activityHeatmap = buildActivityHeatmap(items, today);

        return new FridgeStatisticsResponse(
                items.size(),
                expiringSoonCount,
                expiredCount,
                categoryDistribution,
                storageLocationDistribution,
                expiryAttentionItems,
                longNeglectedItems,
                activityHeatmap
        );
    }

    private String categoryNameOf(FridgeItem item) {
        return item.getIngredient().getCategory() != null
                ? item.getIngredient().getCategory().getName()
                : UNCATEGORIZED_LABEL;
    }

    /** 최근 ACTIVITY_HEATMAP_DAYS일 동안 날짜별로 몇 개의 재료가 새로 등록됐는지(FridgeItem.createdAt 기준) 집계한다. */
    private List<DailyActivityCount> buildActivityHeatmap(List<FridgeItem> items, LocalDate today) {
        LocalDate start = today.minusDays(ACTIVITY_HEATMAP_DAYS - 1L);
        Map<LocalDate, Long> countsByDate = items.stream()
                .map(item -> item.getCreatedAt().toLocalDate())
                .filter(date -> !date.isBefore(start) && !date.isAfter(today))
                .collect(Collectors.groupingBy(date -> date, Collectors.counting()));

        return Stream.iterate(start, date -> date.plusDays(1))
                .limit(ACTIVITY_HEATMAP_DAYS)
                .map(date -> new DailyActivityCount(date, countsByDate.getOrDefault(date, 0L)))
                .toList();
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
