package com.cookgenie.domain.fridge.dto;

import java.time.LocalDate;

/** 특정 날짜에 냉장고에 새로 등록된 재료 수(FridgeItem.createdAt 기준). 활동 히트맵을 그리는 데 쓴다. */
public record DailyActivityCount(LocalDate date, long count) {
}
