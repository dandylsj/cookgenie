package com.cookgenie.domain.shopping.external;

import java.util.List;

/** 쿠팡파트너스 Open API 상품검색(products/search) 응답. */
public record CoupangSearchResponse(String rCode, String rMessage, Data data) {

    public record Data(String landingUrl, List<ProductData> productData) {}

    public record ProductData(
            Long productId,
            String productName,
            String productImage,
            Long productPrice,
            String productUrl,
            Boolean isRocket,
            Boolean isFreeShipping,
            String categoryName
    ) {}
}
