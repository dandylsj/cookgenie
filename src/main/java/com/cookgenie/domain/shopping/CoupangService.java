package com.cookgenie.domain.shopping;

import com.cookgenie.domain.shopping.dto.CoupangProductResponse;
import com.cookgenie.domain.shopping.external.CoupangProductClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 재료/상품명으로 쿠팡 최저가 상품을 검색하는 서비스. */
@Service
@RequiredArgsConstructor
public class CoupangService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final CoupangProductClient coupangProductClient;

    public List<CoupangProductResponse> searchProducts(String keyword, Integer limit) {
        int size = limit != null && limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        return coupangProductClient.search(keyword, size).stream()
                .map(CoupangProductResponse::new)
                .toList();
    }
}
