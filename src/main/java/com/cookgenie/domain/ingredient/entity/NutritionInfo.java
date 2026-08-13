package com.cookgenie.domain.ingredient.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nutrition_infos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NutritionInfo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false, unique = true)
    private Ingredient ingredient;

    @Column(name = "reference_amount", nullable = false)
    @Builder.Default
    private Integer referenceAmount = 100;

    @Column(name = "reference_unit", length = 10)
    private String referenceUnit;

    @Column
    private Integer calories;

    @Column(name = "carbohydrate_g", precision = 5, scale = 1)
    private BigDecimal carbohydrateG;

    @Column(name = "protein_g", precision = 5, scale = 1)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 5, scale = 1)
    private BigDecimal fatG;

    @Column(name = "sugar_g", precision = 5, scale = 1)
    private BigDecimal sugarG;

    @Column(name = "sodium_mg", precision = 7, scale = 1)
    private BigDecimal sodiumMg;

    @Column(name = "fiber_g", precision = 5, scale = 1)
    private BigDecimal fiberG;
}
