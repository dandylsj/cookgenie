package com.cookgenie.domain.meallog.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import com.cookgenie.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "nutrition_goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NutritionGoal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_calories")
    private Integer targetCalories;

    @Column(name = "target_carbohydrate_g", precision = 5, scale = 1)
    private BigDecimal targetCarbohydrateG;

    @Column(name = "target_protein_g", precision = 5, scale = 1)
    private BigDecimal targetProteinG;

    @Column(name = "target_fat_g", precision = 5, scale = 1)
    private BigDecimal targetFatG;

    @Column(name = "weight_kg", precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "activity_level", length = 10)
    private String activityLevel;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
}
