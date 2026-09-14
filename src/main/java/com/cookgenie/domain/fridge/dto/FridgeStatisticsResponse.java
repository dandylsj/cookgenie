package com.cookgenie.domain.fridge.dto;

import java.util.List;
import lombok.Getter;

/** 냉장고 재료 현황 통계(총 개수/임박/지남, 카테고리·보관위치 분포, 주의가 필요한 재료, 등록 활동 히트맵). */
@Getter
public class FridgeStatisticsResponse {

    private final long totalItemCount;
    private final long expiringSoonCount;
    private final long expiredCount;
    private final List<CategoryDistribution> categoryDistribution;
    private final List<StorageLocationDistribution> storageLocationDistribution;
    /** 소비기한이 임박했거나 이미 지난 재료. 임박/지남을 함께 담아 만료일이 가까운(또는 지난) 순으로 정렬한다. */
    private final List<FridgeItemResponse> expiryAttentionItems;
    /** 구매일이 가장 오래된(오래 방치된) 재료. */
    private final List<FridgeItemResponse> longNeglectedItems;
    private final List<DailyActivityCount> activityHeatmap;

    public FridgeStatisticsResponse(
            long totalItemCount,
            long expiringSoonCount,
            long expiredCount,
            List<CategoryDistribution> categoryDistribution,
            List<StorageLocationDistribution> storageLocationDistribution,
            List<FridgeItemResponse> expiryAttentionItems,
            List<FridgeItemResponse> longNeglectedItems,
            List<DailyActivityCount> activityHeatmap) {
        this.totalItemCount = totalItemCount;
        this.expiringSoonCount = expiringSoonCount;
        this.expiredCount = expiredCount;
        this.categoryDistribution = categoryDistribution;
        this.storageLocationDistribution = storageLocationDistribution;
        this.expiryAttentionItems = expiryAttentionItems;
        this.longNeglectedItems = longNeglectedItems;
        this.activityHeatmap = activityHeatmap;
    }
}
