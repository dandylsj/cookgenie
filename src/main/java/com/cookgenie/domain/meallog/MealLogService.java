package com.cookgenie.domain.meallog;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import com.cookgenie.domain.meallog.dto.DailyMealLogResponse;
import com.cookgenie.domain.meallog.dto.DailyMealSummaryResponse;
import com.cookgenie.domain.meallog.dto.MealLogCreateRequest;
import com.cookgenie.domain.meallog.dto.MealLogItemRequest;
import com.cookgenie.domain.meallog.dto.MealLogResponse;
import com.cookgenie.domain.meallog.dto.MealSlotResponse;
import com.cookgenie.domain.meallog.dto.MealSummaryItem;
import com.cookgenie.domain.meallog.entity.MealLog;
import com.cookgenie.domain.meallog.entity.MealLogItem;
import com.cookgenie.domain.meallog.entity.MealLogType;
import com.cookgenie.domain.meallog.entity.MealType;
import com.cookgenie.domain.meallog.repository.MealLogItemRepository;
import com.cookgenie.domain.meallog.repository.MealLogRepository;
import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.recipe.repository.RecipeRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 식단 기록(MealLog) CRUD 및 하루/달력 요약. */
@Service
@RequiredArgsConstructor
public class MealLogService {

    private final MealLogRepository mealLogRepository;
    private final MealLogItemRepository mealLogItemRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final NutritionInfoRepository nutritionInfoRepository;
    private final UserRepository userRepository;
    private final NutritionGoalService nutritionGoalService;

    /** 식단 기록을 추가한다. logType=RECIPE면 recipeId(+servings)로, FREEFORM이면 items로 영양정보를 계산한다. */
    @Transactional
    public MealLogResponse createMealLog(Long userId, MealLogCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        if (request.getLogType() == MealLogType.RECIPE) {
            return createFromRecipe(user, request);
        }
        return createFreeform(user, request);
    }

    private MealLogResponse createFromRecipe(User user, MealLogCreateRequest request) {
        if (request.getRecipeId() == null) {
            throw new CustomException(ErrorMessage.MEAL_LOG_RECIPE_REQUIRED);
        }
        Recipe recipe = recipeRepository.findById(request.getRecipeId())
                .orElseThrow(() -> new CustomException(ErrorMessage.RECIPE_NOT_FOUND));

        BigDecimal servings = request.getServingsOrDefault();
        MealLog mealLog = mealLogRepository.save(
                MealLog.builder()
                        .user(user)
                        .mealDate(request.getMealDate())
                        .mealType(request.getMealType())
                        .logType(MealLogType.RECIPE)
                        .recipe(recipe)
                        .servings(servings)
                        .totalCalories(scaleInt(recipe.getCaloriesPerServing(), servings))
                        .totalCarbohydrateG(scaleDecimal(recipe.getCarbohydrateG(), servings))
                        .totalProteinG(scaleDecimal(recipe.getProteinG(), servings))
                        .totalFatG(scaleDecimal(recipe.getFatG(), servings))
                        .build()
        );
        return new MealLogResponse(mealLog, List.of());
    }

    private MealLogResponse createFreeform(User user, MealLogCreateRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new CustomException(ErrorMessage.MEAL_LOG_ITEMS_REQUIRED);
        }

        MealLog mealLog = mealLogRepository.save(
                MealLog.builder()
                        .user(user)
                        .mealDate(request.getMealDate())
                        .mealType(request.getMealType())
                        .logType(MealLogType.FREEFORM)
                        .build()
        );

        List<MealLogItem> savedItems = new ArrayList<>();
        int totalCalories = 0;
        BigDecimal totalCarbohydrateG = BigDecimal.ZERO;
        BigDecimal totalProteinG = BigDecimal.ZERO;
        BigDecimal totalFatG = BigDecimal.ZERO;

        for (MealLogItemRequest itemRequest : request.getItems()) {
            Ingredient ingredient = ingredientRepository.findById(itemRequest.getIngredientId())
                    .orElseThrow(() -> new CustomException(ErrorMessage.INGREDIENT_NOT_FOUND));
            NutritionInfo nutritionInfo = nutritionInfoRepository.findByIngredientId(ingredient.getId()).orElse(null);
            BigDecimal ratio = resolveRatio(itemRequest.getQuantity(), itemRequest.getUnit(), nutritionInfo);

            Integer calories = scaleInt(nutritionInfo != null ? nutritionInfo.getCalories() : null, ratio);
            BigDecimal carbohydrateG = scaleDecimal(nutritionInfo != null ? nutritionInfo.getCarbohydrateG() : null, ratio);
            BigDecimal proteinG = scaleDecimal(nutritionInfo != null ? nutritionInfo.getProteinG() : null, ratio);
            BigDecimal fatG = scaleDecimal(nutritionInfo != null ? nutritionInfo.getFatG() : null, ratio);

            savedItems.add(mealLogItemRepository.save(
                    MealLogItem.builder()
                            .mealLog(mealLog)
                            .ingredient(ingredient)
                            .quantity(itemRequest.getQuantity())
                            .unit(itemRequest.getUnit())
                            .calories(calories)
                            .carbohydrateG(carbohydrateG)
                            .proteinG(proteinG)
                            .fatG(fatG)
                            .build()
            ));

            totalCalories += calories != null ? calories : 0;
            totalCarbohydrateG = totalCarbohydrateG.add(carbohydrateG != null ? carbohydrateG : BigDecimal.ZERO);
            totalProteinG = totalProteinG.add(proteinG != null ? proteinG : BigDecimal.ZERO);
            totalFatG = totalFatG.add(fatG != null ? fatG : BigDecimal.ZERO);
        }

        mealLog.updateTotals(
                totalCalories,
                totalCarbohydrateG.setScale(1, RoundingMode.HALF_UP),
                totalProteinG.setScale(1, RoundingMode.HALF_UP),
                totalFatG.setScale(1, RoundingMode.HALF_UP)
        );

        return new MealLogResponse(mealLog, savedItems);
    }

    /** 특정 날짜의 6개 식사 슬롯(아침~저녁간식)과 그날 목표 대비 총 섭취량을 조회한다. */
    @Transactional(readOnly = true)
    public DailyMealLogResponse getDaily(Long userId, LocalDate date) {
        List<MealLog> logs = mealLogRepository.findByUserIdAndMealDate(userId, date);
        Map<MealType, List<MealLogResponse>> byType = new LinkedHashMap<>();
        for (MealType type : MealType.values()) {
            byType.put(type, new ArrayList<>());
        }
        for (MealLog log : logs) {
            List<MealLogItem> items = log.getLogType() == MealLogType.FREEFORM
                    ? mealLogItemRepository.findByMealLogId(log.getId())
                    : List.of();
            byType.get(log.getMealType()).add(new MealLogResponse(log, items));
        }

        List<MealSlotResponse> slots = byType.entrySet().stream()
                .map(entry -> new MealSlotResponse(entry.getKey(), entry.getValue()))
                .toList();

        return new DailyMealLogResponse(date, slots, nutritionGoalService.getCurrent(userId, date));
    }

    /** startDate~endDate 범위의 날짜별 식사 요약(달력용)을 조회한다. 기록이 없는 날짜는 결과에 포함되지 않는다. */
    @Transactional(readOnly = true)
    public List<DailyMealSummaryResponse> getCalendarSummary(Long userId, LocalDate startDate, LocalDate endDate) {
        List<MealLog> logs = mealLogRepository.findByUserIdAndMealDateBetween(userId, startDate, endDate);

        Map<LocalDate, List<MealLog>> byDate = logs.stream()
                .collect(Collectors.groupingBy(MealLog::getMealDate, LinkedHashMap::new, Collectors.toList()));

        return byDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<MealLog> dayLogs = entry.getValue();
                    int totalCalories = dayLogs.stream()
                            .mapToInt(log -> log.getTotalCalories() != null ? log.getTotalCalories() : 0)
                            .sum();
                    List<MealSummaryItem> meals = dayLogs.stream()
                            .sorted(Comparator.comparing(MealLog::getMealType))
                            .map(log -> new MealSummaryItem(log.getMealType(), labelOf(log)))
                            .toList();
                    return new DailyMealSummaryResponse(date, totalCalories, meals);
                })
                .sorted(Comparator.comparing(DailyMealSummaryResponse::getDate))
                .toList();
    }

    /** 식단 기록 삭제. 본인 기록만 삭제 가능하다. */
    @Transactional
    public void deleteMealLog(Long userId, Long mealLogId) {
        MealLog mealLog = mealLogRepository.findById(mealLogId)
                .orElseThrow(() -> new CustomException(ErrorMessage.MEAL_LOG_NOT_FOUND));
        if (!mealLog.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorMessage.MEAL_LOG_NOT_FOUND);
        }
        mealLogItemRepository.deleteAll(mealLogItemRepository.findByMealLogId(mealLogId));
        mealLogRepository.delete(mealLog);
    }

    private String labelOf(MealLog log) {
        if (log.getLogType() == MealLogType.RECIPE) {
            return log.getRecipe() != null ? log.getRecipe().getTitle() : "";
        }
        List<MealLogItem> items = mealLogItemRepository.findByMealLogId(log.getId());
        if (items.isEmpty()) {
            return "";
        }
        String first = items.get(0).getIngredient() != null ? items.get(0).getIngredient().getName() : "";
        return items.size() > 1 ? first + " 외 " + (items.size() - 1) + "개" : first;
    }

    /** 단위가 일치할 때만(예: g-g) quantity/referenceAmount 비율을 계산하고, 아니면 null(계산 불가). */
    private static BigDecimal resolveRatio(BigDecimal quantity, String unit, NutritionInfo nutritionInfo) {
        if (nutritionInfo == null || nutritionInfo.getReferenceAmount() == null
                || nutritionInfo.getReferenceUnit() == null || unit == null
                || !nutritionInfo.getReferenceUnit().equalsIgnoreCase(unit)) {
            return null;
        }
        return quantity.divide(BigDecimal.valueOf(nutritionInfo.getReferenceAmount()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleDecimal(BigDecimal perReference, BigDecimal ratio) {
        if (perReference == null || ratio == null) {
            return null;
        }
        return perReference.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
    }

    private static Integer scaleInt(Integer perReference, BigDecimal ratio) {
        if (perReference == null || ratio == null) {
            return null;
        }
        return BigDecimal.valueOf(perReference).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
