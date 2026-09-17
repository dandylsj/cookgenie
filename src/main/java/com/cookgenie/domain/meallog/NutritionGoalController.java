package com.cookgenie.domain.meallog;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.meallog.dto.NutritionGoalRequest;
import com.cookgenie.domain.meallog.dto.NutritionGoalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 목표 칼로리/탄단지 설정·조회 API. */
@Tag(name = "목표 영양정보(NutritionGoal)", description = "하루 목표 칼로리/탄단지 설정 및 조회 API")
@RestController
@RequestMapping("/nutrition-goals")
@RequiredArgsConstructor
public class NutritionGoalController {

    private final NutritionGoalService nutritionGoalService;
    private final JwtUtil jwtUtil;

    /** POST /nutrition-goals - 목표 등록 (effectiveDate 생략 시 오늘부터 적용) */
    @Operation(summary = "목표 영양정보 등록", description = "하루 목표 칼로리/탄단지(및 체중/키/활동량)를 등록합니다.")
    @PostMapping
    public ResponseEntity<GlobalResponse<NutritionGoalResponse>> setGoal(
            @RequestHeader("Authorization") String accessToken, @RequestBody NutritionGoalRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(
                nutritionGoalService.setGoal(resolveUserId(accessToken), request)));
    }

    /** GET /nutrition-goals/current?date= - 현재(또는 지정일 기준) 적용 중인 목표 조회. 없으면 data가 null. */
    @Operation(
            summary = "현재 적용 목표 조회",
            description = "date를 생략하면 오늘 기준으로, 지정하면 그 날짜 기준으로 적용 중인 목표를 조회합니다. 설정된 목표가 없으면 data가 null입니다."
    )
    @GetMapping("/current")
    public ResponseEntity<GlobalResponse<NutritionGoalResponse>> getCurrent(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "기준 날짜 (생략 시 오늘)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(GlobalResponse.success(
                nutritionGoalService.getCurrent(resolveUserId(accessToken), date)));
    }

    private Long resolveUserId(String accessToken) {
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        jwtUtil.validateToken(token);
        return jwtUtil.extractUserId(token);
    }
}
