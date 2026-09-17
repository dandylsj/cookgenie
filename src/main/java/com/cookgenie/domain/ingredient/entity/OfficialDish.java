package com.cookgenie.domain.ingredient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식약처 "전국통합식품영양성분정보(음식)" 공공데이터를 통째로 로컬 DB에 복사해둔 검색용 테이블. 짜장면/
 * 김치찌개 같은 조리된 메뉴 기준 영양정보라 배달/외식 음식 등록에 쓴다. {@link OfficialProcessedFood}(가공식품)
 * 와 개념이 달라서 별도 테이블로 둔다 - foodCd가 진짜 개별 항목 식별자인지 확실치 않아서(가공식품에서 이
 * 가정이 틀렸던 적이 있음) 유니크 제약은 (food_cd, food_nm, rest_nm) 조합으로 건다.
 */
@Entity
@Table(name = "official_dishes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_official_dish_cd_nm_rest", columnNames = {"food_cd", "food_nm", "rest_nm"})
}, indexes = {
        @Index(name = "idx_official_dish_code", columnList = "food_cd"),
        @Index(name = "idx_official_dish_name", columnList = "food_nm"),
        @Index(name = "idx_official_dish_rest", columnList = "rest_nm")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OfficialDish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_cd", nullable = false, length = 40)
    private String foodCd;

    @Column(name = "food_nm", nullable = false, length = 200)
    private String foodNm;

    @Column(name = "rest_nm", length = 200)
    private String restNm;

    @Column(name = "reference_unit", length = 10)
    private String referenceUnit;

    @Column
    private Integer calories;

    @Column(name = "carbohydrate_g", precision = 8, scale = 1)
    private BigDecimal carbohydrateG;

    @Column(name = "protein_g", precision = 8, scale = 1)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 8, scale = 1)
    private BigDecimal fatG;

    @Column(name = "sugar_g", precision = 8, scale = 1)
    private BigDecimal sugarG;

    @Column(name = "sodium_mg", precision = 10, scale = 1)
    private BigDecimal sodiumMg;

    @Column(name = "fiber_g", precision = 8, scale = 1)
    private BigDecimal fiberG;
}
