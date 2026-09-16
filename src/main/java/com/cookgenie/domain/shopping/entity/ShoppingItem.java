package com.cookgenie.domain.shopping.entity;

import com.cookgenie.common.entity.BaseTimeEntity;
import com.cookgenie.domain.fridge.entity.Fridge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "shopping_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ShoppingItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "is_checked", nullable = false)
    @Builder.Default
    private boolean checked = false;

    public void updateChecked(boolean checked) {
        this.checked = checked;
    }
}
