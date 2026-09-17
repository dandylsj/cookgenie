package com.cookgenie.domain.meallog;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.meallog.dto.NutritionGoalRequest;
import com.cookgenie.domain.meallog.dto.NutritionGoalResponse;
import com.cookgenie.domain.meallog.entity.NutritionGoal;
import com.cookgenie.domain.meallog.repository.NutritionGoalRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 목표 칼로리/탄단지(NutritionGoal) 설정·조회. */
@Service
@RequiredArgsConstructor
public class NutritionGoalService {

    private final NutritionGoalRepository nutritionGoalRepository;
    private final UserRepository userRepository;

    /**
     * 새 목표를 등록한다(같은 effectiveDate로 여러 번 등록해도 별도 이력으로 쌓임 — 최신 등록분이
     * getCurrent()에서 우선 조회됨은 아니고, effectiveDate 기준 최신값이 조회되므로 보통 오늘 날짜로 갱신하면 됨).
     */
    @Transactional
    public NutritionGoalResponse setGoal(Long userId, NutritionGoalRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        NutritionGoal goal = nutritionGoalRepository.save(
                NutritionGoal.builder()
                        .user(user)
                        .targetCalories(request.getTargetCalories())
                        .targetCarbohydrateG(request.getTargetCarbohydrateG())
                        .targetProteinG(request.getTargetProteinG())
                        .targetFatG(request.getTargetFatG())
                        .weightKg(request.getWeightKg())
                        .heightCm(request.getHeightCm())
                        .activityLevel(request.getActivityLevel())
                        .effectiveDate(request.getEffectiveDateOrToday())
                        .build()
        );
        return new NutritionGoalResponse(goal);
    }

    /** date 기준으로 적용 중인 목표(effectiveDate가 date 이하인 것 중 가장 최근)를 조회한다. 없으면 null. */
    @Transactional(readOnly = true)
    public NutritionGoalResponse getCurrent(Long userId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return nutritionGoalRepository
                .findFirstByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(userId, target)
                .map(NutritionGoalResponse::new)
                .orElse(null);
    }
}
