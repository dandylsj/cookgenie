package com.cookgenie.domain.fridge.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import com.cookgenie.domain.ingredient.entity.Ingredient;
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
@Table(name = "fridge_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FridgeItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, length = 10)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_location", nullable = false, length = 20)
    private StorageLocation storageLocation;

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(length = 200)
    private String memo;

    public void update(BigDecimal quantity, String unit, StorageLocation storageLocation,
                        LocalDate purchasedAt, LocalDate expiryDate, String memo) {
        this.quantity = quantity;
        this.unit = unit;
        this.storageLocation = storageLocation;
        this.purchasedAt = purchasedAt;
        this.expiryDate = expiryDate;
        this.memo = memo;
    }
}
