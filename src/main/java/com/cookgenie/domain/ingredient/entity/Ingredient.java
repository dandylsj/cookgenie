package com.cookgenie.domain.ingredient.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Ingredient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingredient_type", nullable = false, length = 20)
    private IngredientType ingredientType;

    @Column(name = "default_unit", length = 10)
    private String defaultUnit;

    @Column(length = 30)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 20)
    private DataSource dataSource;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    public void update(String name, Category category, String defaultUnit) {
        this.name = name;
        this.category = category;
        this.defaultUnit = defaultUnit;
    }

    /** 사용자가 영양정보를 직접 입력/수정했을 때 호출한다. */
    public void markNutritionVerified() {
        this.dataSource = DataSource.USER_INPUT;
        this.isVerified = true;
    }

    /** Claude 추정으로 영양정보를 (다시) 채웠을 때 호출한다. */
    public void markNutritionEstimated() {
        this.dataSource = DataSource.LLM_ESTIMATED;
        this.isVerified = false;
    }

    /**
     * 공공데이터 동기화로 이 재료를 정부 공식 데이터로 승격시킬 때 호출한다. AI 추정이었거나 아직
     * 검증되지 않은 재료를 공식 데이터로 업그레이드하는 용도 - 사용자가 직접 입력해서 검증한 값은
     * 이 메서드를 호출하기 전에 먼저 걸러내야 한다(덮어쓰면 안 됨).
     */
    public void markOfficial(Category category, String defaultUnit, IngredientType ingredientType) {
        this.category = category;
        this.defaultUnit = defaultUnit;
        this.ingredientType = ingredientType;
        this.dataSource = DataSource.OFFICIAL_DB;
        this.isVerified = true;
    }
}
