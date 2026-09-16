package com.cookgenie.domain.shopping;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.shopping.dto.CoupangProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 쿠팡파트너스 Open API를 이용한 최저가 상품 검색 API. */
@Tag(name = "쿠팡 최저가(Coupang)", description = "재료/상품명으로 쿠팡 최저가 상품을 검색합니다(쿠팡파트너스 Open API 연동).")
@RestController
@RequestMapping("/coupang")
@RequiredArgsConstructor
public class CoupangController {

    private final CoupangService coupangService;

    /** GET /coupang/search?keyword=&limit= - 키워드로 쿠팡 상품 검색 */
    @Operation(
            summary = "쿠팡 최저가 검색",
            description = "keyword(재료/상품명)로 쿠팡 상품을 검색합니다. 쿠팡파트너스 활동을 통한 결과이며, "
                    + "구매 시 일정액의 수수료가 발생할 수 있습니다."
    )
    @GetMapping("/search")
    public ResponseEntity<GlobalResponse<List<CoupangProductResponse>>> search(
            @Parameter(description = "검색어") @RequestParam String keyword,
            @Parameter(description = "최대 개수 (기본 10, 최대 30)") @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(GlobalResponse.success(coupangService.searchProducts(keyword, limit)));
    }
}
