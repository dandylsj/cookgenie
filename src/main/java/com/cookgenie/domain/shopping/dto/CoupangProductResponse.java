package com.cookgenie.domain.shopping.dto;

import com.cookgenie.domain.shopping.external.CoupangSearchResponse;
import lombok.Getter;

@Getter
public class CoupangProductResponse {

    private final Long productId;
    private final String name;
    private final long price;
    private final String imageUrl;
    private final String productUrl;
    private final boolean isRocket;
    private final boolean isFreeShipping;

    public CoupangProductResponse(CoupangSearchResponse.ProductData product) {
        this.productId = product.productId();
        this.name = product.productName();
        this.price = product.productPrice() != null ? product.productPrice() : 0L;
        this.imageUrl = product.productImage();
        this.productUrl = product.productUrl();
        this.isRocket = Boolean.TRUE.equals(product.isRocket());
        this.isFreeShipping = Boolean.TRUE.equals(product.isFreeShipping());
    }
}
