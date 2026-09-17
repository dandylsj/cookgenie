package com.cookgenie.domain.product;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.receipt.dto.ReceiptScanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 실물 상품 사진 인식 API. 인식 결과는 저장되지 않는 미리보기이며, 실제 등록은 기존 식재료/냉장고 재료 API를 재사용한다. */
@Tag(name = "실물 상품 인식(Product)", description = "실물 식품 상품 사진을 분석해서 식재료 후보 목록을 추출합니다(등록 전 미리보기).")
@RestController
@RequestMapping("/fridges/{fridgeId}/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** POST /fridges/{fridgeId}/products/scan - 실물 상품 사진 스캔 (미리보기, 등록 안 함) */
    @Operation(
            summary = "실물 상품 사진 인식",
            description = "식품 포장/라벨 사진(JPEG/PNG/WEBP, 최대 10MB)을 업로드하면 Claude가 사진에 적힌 상품명을 "
                    + "읽어서 식재료 후보를 추출합니다. 응답 형태와 매칭 규칙(matchedIngredientId/matchedProcessedFood/"
                    + "matchedDish)은 영수증 스캔과 완전히 동일합니다 - matchedProcessedFood나 matchedDish가 있으면 "
                    + "AI 추정 호출 없이 그 값을 그대로 POST /ingredients의 직접 입력값으로 넘겨 등록할 수 있습니다. "
                    + "이 API도 미리보기만 하고 바로 등록하지 않습니다."
    )
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GlobalResponse<ReceiptScanResponse>> scanProduct(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "실물 상품 이미지 파일") @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(GlobalResponse.success(productService.scanProduct(fridgeId, image)));
    }
}
