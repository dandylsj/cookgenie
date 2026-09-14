package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.dto.RawMaterialSyncResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 원재료 영양정보 공공데이터를 매달 자동으로 재동기화한다 (원본 데이터가 연 1회 정도 갱신되므로 여유 있게 매달 실행). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawMaterialSyncScheduler {

    private final IngredientService ingredientService;

    @Scheduled(cron = "0 0 3 1 * *")
    public void syncMonthly() {
        log.info("[MFDS 동기화] 월간 자동 동기화 시작");
        RawMaterialSyncResponse result = ingredientService.syncRawMaterialsFromMfds();
        log.info("[MFDS 동기화] 월간 자동 동기화 완료 - 조회 {}건, 생성 {}건, 갱신 {}건, 실패 {}건",
                result.getTotalFetched(), result.getCreated(), result.getUpdated(), result.getFailed());
    }
}
