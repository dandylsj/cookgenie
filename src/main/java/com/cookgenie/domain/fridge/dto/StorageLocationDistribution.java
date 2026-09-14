package com.cookgenie.domain.fridge.dto;

import com.cookgenie.domain.fridge.entity.StorageLocation;

/** 냉장고 재료의 보관 위치별 개수. */
public record StorageLocationDistribution(StorageLocation storageLocation, long count) {
}
