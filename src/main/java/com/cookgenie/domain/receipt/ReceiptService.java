package com.cookgenie.domain.receipt;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.ingredient.IngredientService;
import com.cookgenie.domain.ingredient.dto.OfficialDishCandidateResponse;
import com.cookgenie.domain.ingredient.dto.OfficialFoodCandidateResponse;
import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import com.cookgenie.domain.receipt.dto.ReceiptItemResponse;
import com.cookgenie.domain.receipt.dto.ReceiptScanResponse;
import com.cookgenie.domain.receipt.external.ClaudeReceiptClient;
import com.cookgenie.domain.receipt.external.ReceiptScanResult;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 영수증 사진/온라인 쇼핑몰 주문내역 캡처를 Claude에게 분석시켜 식재료 후보 목록을 뽑아주는 서비스.
 * 냉장고/식재료 마스터에 아무것도 쓰지 않는 순수 미리보기(preview)이며, 실제 등록은 프론트에서
 * 기존 POST /ingredients + POST /fridges/{fridgeId}/items를 사용자가 확인한 항목만 골라 호출한다.
 *
 * <p>{@link #toItemResponse}는 {@link com.cookgenie.domain.product.ProductService}(실물 상품 사진 인식)도
 * 재사용한다 - "사진에서 뽑아낸 이름을 기존 재료/공식 데이터와 맞춰보는" 로직이 입력 방식(영수증/주문내역/
 * 실물 사진)과 무관하게 완전히 동일하기 때문.
 */
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final FridgeRepository fridgeRepository;
    private final IngredientService ingredientService;
    private final CategoryRepository categoryRepository;
    private final ClaudeReceiptClient claudeReceiptClient;

    /** 영수증 이미지를 분석해서 식재료 후보 목록(미리보기)을 반환한다. */
    @Transactional(readOnly = true)
    public ReceiptScanResponse scanReceipt(Long fridgeId, MultipartFile image) {
        ValidatedImage validated = validateImage(fridgeId, image);
        ReceiptScanResult result = claudeReceiptClient.scan(validated.mediaType(), validated.base64())
                .orElseThrow(() -> new CustomException(ErrorMessage.RECEIPT_SCAN_FAILED));
        return toResponse(result);
    }

    /** 온라인 쇼핑몰 주문내역(구매내역) 화면 캡처를 분석해서 식재료 후보 목록(미리보기)을 반환한다. */
    @Transactional(readOnly = true)
    public ReceiptScanResponse scanOrderHistory(Long fridgeId, MultipartFile image) {
        ValidatedImage validated = validateImage(fridgeId, image);
        ReceiptScanResult result = claudeReceiptClient.scanOrderHistory(validated.mediaType(), validated.base64())
                .orElseThrow(() -> new CustomException(ErrorMessage.RECEIPT_SCAN_FAILED));
        return toResponse(result);
    }

    private ReceiptScanResponse toResponse(ReceiptScanResult result) {
        List<ReceiptItemResponse> items = result.items() == null
                ? List.of()
                : result.items().stream().map(this::toItemResponse).toList();
        return new ReceiptScanResponse(items);
    }

    /**
     * 인식된 항목 하나를 (1) 기존 식재료 마스터, (2) 식약처 가공식품 로컬 미러, (3) 식약처 음식 로컬 미러
     * 순서로 이름 매칭을 시도해서 응답 DTO로 변환한다. 이미 등록된 식재료가 있으면 그걸로 충분하므로
     * 공식 데이터 매칭은 그때만 건너뛴다(불필요한 조회 방지).
     */
    public ReceiptItemResponse toItemResponse(ReceiptScanResult.ScannedItem item) {
        Long matchedIngredientId = ingredientService.matchByName(item.name())
                .map(Ingredient::getId)
                .orElse(null);
        Long matchedCategoryId = item.categoryNameGuess() == null
                ? null
                : categoryRepository.findByName(item.categoryNameGuess()).map(Category::getId).orElse(null);

        OfficialFoodCandidateResponse matchedProcessedFood = null;
        OfficialDishCandidateResponse matchedDish = null;
        if (matchedIngredientId == null) {
            matchedProcessedFood = ingredientService.matchProcessedFoodByName(item.name())
                    .map(OfficialFoodCandidateResponse::new)
                    .orElse(null);
            if (matchedProcessedFood == null) {
                matchedDish = ingredientService.matchDishByName(item.name())
                        .map(OfficialDishCandidateResponse::new)
                        .orElse(null);
            }
        }

        return new ReceiptItemResponse(item, matchedIngredientId, matchedCategoryId, matchedProcessedFood, matchedDish);
    }

    private ValidatedImage validateImage(Long fridgeId, MultipartFile image) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }
        if (image == null || image.isEmpty()) {
            throw new CustomException(ErrorMessage.RECEIPT_IMAGE_REQUIRED);
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new CustomException(ErrorMessage.RECEIPT_IMAGE_TOO_LARGE);
        }
        return new ValidatedImage(resolveMediaType(image), encode(image));
    }

    private String resolveMediaType(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_MEDIA_TYPES.contains(contentType)) {
            throw new CustomException(ErrorMessage.UNSUPPORTED_IMAGE_TYPE);
        }
        return contentType;
    }

    private String encode(MultipartFile image) {
        try {
            return Base64.getEncoder().encodeToString(image.getBytes());
        } catch (IOException e) {
            throw new CustomException(ErrorMessage.RECEIPT_SCAN_FAILED);
        }
    }

    private record ValidatedImage(String mediaType, String base64) {
    }
}
