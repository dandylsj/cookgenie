package com.cookgenie.domain.recipe.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Recipe extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipe_type", nullable = false, length = 20)
    private RecipeType recipeType;

    @Column(name = "cooking_type", length = 20)
    private String cookingType;

    @Column(name = "source_url", length = 255)
    private String sourceUrl;

    @Column(name = "author_nickname", length = 50)
    private String authorNickname;

    @Column(name = "serving_size")
    private Integer servingSize;

    @Column(name = "calories_per_serving")
    private Integer caloriesPerServing;

    @Column(name = "carbohydrate_g", precision = 5, scale = 1)
    private BigDecimal carbohydrateG;

    @Column(name = "protein_g", precision = 5, scale = 1)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 5, scale = 1)
    private BigDecimal fatG;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "save_count", nullable = false)
    @Builder.Default
    private Integer saveCount = 0;
}
