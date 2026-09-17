package com.cookgenie.domain.receipt;

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

/** 영수증/주문내역 사진 인식 API. 인식 결과는 저장되지 않는 미리보기이며, 실제 등록은 기존 식재료/냉장고 재료 API를 재사용한다. */
@Tag(name = "영수증 인식(Receipt)", description = "영수증/주문내역 사진을 분석해서 식재료 후보 목록을 추출합니다(등록 전 미리보기).")
@RestController
@RequestMapping("/fridges/{fridgeId}/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    /** POST /fridges/{fridgeId}/receipts/scan - 영수증 사진 스캔 (미리보기, 등록 안 함) */
    @Operation(
            summary = "영수증 스캔",
            description = "영수증 사진(JPEG/PNG/WEBP, 최대 10MB)을 업로드하면 Claude가 이미지를 분석해서 "
                    + "식재료로 보이는 품목들의 이름/수량/카테고리 추정치를 추출합니다. **이 API는 미리보기만 하고 "
                    + "바로 냉장고에 등록하지 않습니다.** 각 항목에 matchedIngredientId/matchedCategoryId가 "
                    + "있으면 기존 마스터와 매칭된 것입니다. 없고 matchedProcessedFood(가공식품) 또는 "
                    + "matchedDish(음식/배달메뉴)가 있으면 식약처 공식 데이터와 이름이 일치한 것이니, AI 추정 "
                    + "호출 없이 그 값을 그대로 POST /ingredients의 직접 입력값(calories 등)으로 넘겨 등록할 수 "
                    + "있습니다. 셋 다 없으면 처음 보는 이름이라 autoEstimateNutrition=true로 AI 추정을 요청하거나 "
                    + "영양정보 없이 등록한 뒤, 확보한 ingredientId로 POST /fridges/{fridgeId}/items를 호출해서 "
                    + "실제로 등록해주세요."
    )
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GlobalResponse<ReceiptScanResponse>> scanReceipt(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "영수증 이미지 파일") @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(GlobalResponse.success(receiptService.scanReceipt(fridgeId, image)));
    }

    /** POST /fridges/{fridgeId}/receipts/scan-order-history - 온라인 쇼핑몰 주문내역 캡처 스캔 (미리보기, 등록 안 함) */
    @Operation(
            summary = "주문내역(구매내역) 캡처 스캔",
            description = "쿠팡/마켓컬리/네이버쇼핑 같은 온라인 쇼핑몰의 주문내역 화면 캡처(JPEG/PNG/WEBP, 최대 10MB)를 "
                    + "업로드하면 Claude가 화면을 분석해서 식재료로 보이는 품목들을 추출합니다. 응답 형태와 매칭 "
                    + "규칙(matchedIngredientId/matchedProcessedFood/matchedDish)은 위 영수증 스캔과 완전히 동일하고, "
                    + "화면 형태(썸네일+상품명+수량+가격 목록)에 맞춰 인식 프롬프트만 다릅니다. 마찬가지로 미리보기만 "
                    + "하고 바로 등록하지 않습니다."
    )
    @PostMapping(value = "/scan-order-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GlobalResponse<ReceiptScanResponse>> scanOrderHistory(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "주문내역 화면 캡처 이미지 파일") @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(GlobalResponse.success(receiptService.scanOrderHistory(fridgeId, image)));
    }
}
