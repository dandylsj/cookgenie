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

/** 영수증 사진 인식 API. 인식 결과는 저장되지 않는 미리보기이며, 실제 등록은 기존 식재료/냉장고 재료 API를 재사용한다. */
@Tag(name = "영수증 인식(Receipt)", description = "영수증 사진을 분석해서 식재료 후보 목록을 추출합니다(등록 전 미리보기).")
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
                    + "있으면 기존 마스터와 매칭된 것이고, 없으면 처음 보는 이름/카테고리입니다. "
                    + "프론트에서 결과를 보여주고 사용자가 확인/수정한 뒤, 항목별로 POST /ingredients(없으면 "
                    + "새로 만들고 있으면 재사용)로 ingredientId를 확보하고, 그 ingredientId로 "
                    + "POST /fridges/{fridgeId}/items를 호출해서 실제로 등록해주세요."
    )
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GlobalResponse<ReceiptScanResponse>> scanReceipt(
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "영수증 이미지 파일") @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(GlobalResponse.success(receiptService.scanReceipt(fridgeId, image)));
    }
}
