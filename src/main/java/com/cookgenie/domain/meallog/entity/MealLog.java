package com.cookgenie.domain.meallog.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import com.cookgenie.domain.recipe.entity.Recipe;
import com.cookgenie.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meal_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MealLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 20)
    private MealLogType logType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(precision = 3, scale = 1)
    private BigDecimal servings;

    @Column(name = "total_calories")
    private Integer totalCalories;

    @Column(name = "total_carbohydrate_g", precision = 5, scale = 1)
    private BigDecimal totalCarbohydrateG;

    @Column(name = "total_protein_g", precision = 5, scale = 1)
    private BigDecimal totalProteinG;

    @Column(name = "total_fat_g", precision = 5, scale = 1)
    private BigDecimal totalFatG;
}
