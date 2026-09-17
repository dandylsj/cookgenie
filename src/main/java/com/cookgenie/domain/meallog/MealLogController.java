package com.cookgenie.domain.meallog;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.meallog.dto.DailyMealLogResponse;
import com.cookgenie.domain.meallog.dto.DailyMealSummaryResponse;
import com.cookgenie.domain.meallog.dto.MealLogCreateRequest;
import com.cookgenie.domain.meallog.dto.MealLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 식단 기록(MealLog) 추가/조회/삭제 API. */
@Tag(name = "식단 기록(MealLog)", description = "하루 식사 기록 추가/조회/삭제, 날짜별·달력 범위 요약 API")
@RestController
@RequestMapping("/meal-logs")
@RequiredArgsConstructor
public class MealLogController {

    private final MealLogService mealLogService;
    private final JwtUtil jwtUtil;

    /** POST /meal-logs - 식단 기록 추가 (레시피 선택 또는 재료 직접입력) */
    @Operation(
            summary = "식단 기록 추가",
            description = "logType=RECIPE면 recipeId(+servings)로, FREEFORM이면 items(재료 목록)로 기록합니다. "
                    + "영양정보는 서버가 자동 계산합니다."
    )
    @PostMapping
    public ResponseEntity<GlobalResponse<MealLogResponse>> createMealLog(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody MealLogCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(
                mealLogService.createMealLog(resolveUserId(accessToken), request)));
    }

    /** GET /meal-logs?date= - 하루 상세 조회 (6개 식사 슬롯 + 목표 대비 총 섭취량) */
    @Operation(
            summary = "하루 식단 상세 조회",
            description = "아침/점심/저녁/오전간식/오후간식/저녁간식 6개 슬롯과 그날 총 섭취 칼로리·탄단지, 목표 대비 값을 함께 내려줍니다."
    )
    @GetMapping
    public ResponseEntity<GlobalResponse<DailyMealLogResponse>> getDaily(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(GlobalResponse.success(
                mealLogService.getDaily(resolveUserId(accessToken), date)));
    }

    /** GET /meal-logs/calendar?startDate=&endDate= - 달력 범위 요약 조회 */
    @Operation(
            summary = "달력용 식단 요약 조회",
            description = "startDate~endDate 범위에서 기록이 있는 날짜만, 날짜별 총 칼로리와 식사별 대표 항목명을 내려줍니다."
    )
    @GetMapping("/calendar")
    public ResponseEntity<GlobalResponse<List<DailyMealSummaryResponse>>> getCalendarSummary(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "조회 시작 날짜 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료 날짜 (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(GlobalResponse.success(
                mealLogService.getCalendarSummary(resolveUserId(accessToken), startDate, endDate)));
    }

    /** DELETE /meal-logs/{mealLogId} - 식단 기록 삭제 (본인 기록만 가능) */
    @Operation(summary = "식단 기록 삭제", description = "본인이 기록한 식사만 삭제할 수 있습니다.")
    @DeleteMapping("/{mealLogId}")
    public ResponseEntity<GlobalResponse<Void>> deleteMealLog(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "식단 기록 ID") @PathVariable Long mealLogId) {
        mealLogService.deleteMealLog(resolveUserId(accessToken), mealLogId);
        return ResponseEntity.ok(GlobalResponse.success(null));
    }

    private Long resolveUserId(String accessToken) {
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        jwtUtil.validateToken(token);
        return jwtUtil.extractUserId(token);
    }
}
