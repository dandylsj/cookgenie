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
}
