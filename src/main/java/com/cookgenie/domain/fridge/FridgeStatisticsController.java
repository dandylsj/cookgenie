package com.cookgenie.domain.fridge;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.fridge.dto.FridgeStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 냉장고 재료 현황 통계 API. */
@Tag(name = "냉장고 통계(FridgeStatistics)", description = "냉장고 재료 현황(임박/지남 개수, 분포, 등록 활동) 통계 API")
@RestController
@RequestMapping("/fridges/{fridgeId}/statistics")
@RequiredArgsConstructor
public class FridgeStatisticsController {

    private final FridgeItemService fridgeItemService;

    /** GET /fridges/{fridgeId}/statistics - 냉장고 재료 현황 통계 조회 */
    @Operation(
            summary = "냉장고 재료 현황 통계",
            description = "총 재료 수, 소비기한 임박/지남 개수, 카테고리·보관위치별 분포, 소비기한이 임박했거나 지난 재료, "
                    + "구매일이 오래된(방치된) 재료, 최근 등록 활동 히트맵을 한번에 조회합니다."
    )
    @GetMapping
    public ResponseEntity<GlobalResponse<FridgeStatisticsResponse>> getStatistics(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeItemService.getStatistics(fridgeId)));
    }
}
