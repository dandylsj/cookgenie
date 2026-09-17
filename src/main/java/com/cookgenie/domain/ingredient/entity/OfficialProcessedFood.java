package com.cookgenie.domain.ingredient.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 식약처 "전국통합식품영양성분정보(가공식품)" 공공데이터를 통째로 로컬 DB에 복사해둔 검색용 테이블
 * (약 59만 건). data.go.kr API 자체는 foodNm이 완전 일치해야만 찾아지고 부분검색(포함검색)이 안 돼서,
 * "실온"→"닭"처럼 좁혀가며 찾는 UX를 만들려면 우리 쪽에 데이터를 미리 복사해두고 LIKE 검색을 해야 한다.
 * {@link com.cookgenie.domain.ingredient.OfficialProcessedFoodSyncService}가 foodNm 필터 없이 페이지를
 * 넘기면서 API 전체를 이 테이블로 복사한다. 값은 전부 100g/100ml 기준으로 정규화되어 저장된다.
 */
@Entity
@Table(name = "official_processed_foods", indexes = {
        @Index(name = "idx_official_processed_food_code", columnList = "food_cd", unique = true),
        @Index(name = "idx_official_processed_food_name", columnList = "food_nm"),
        @Index(name = "idx_official_processed_food_mfr", columnList = "mfr_nm")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OfficialProcessedFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "food_cd", nullable = false, length = 40)
    private String foodCd;

    @Column(name = "food_nm", nullable = false, length = 200)
    private String foodNm;

    @Column(name = "mfr_nm", length = 200)
    private String mfrNm;

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
