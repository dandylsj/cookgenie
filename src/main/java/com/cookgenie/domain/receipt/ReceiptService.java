package com.cookgenie.domain.receipt;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.ingredient.IngredientService;
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
 * 영수증 사진을 Claude에게 분석시켜 식재료 후보 목록을 뽑아주는 서비스.
 * 냉장고/식재료 마스터에 아무것도 쓰지 않는 순수 미리보기(preview)이며, 실제 등록은 프론트에서
 * 기존 POST /ingredients + POST /fridges/{fridgeId}/items를 사용자가 확인한 항목만 골라 호출한다.
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
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }
        if (image == null || image.isEmpty()) {
            throw new CustomException(ErrorMessage.RECEIPT_IMAGE_REQUIRED);
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new CustomException(ErrorMessage.RECEIPT_IMAGE_TOO_LARGE);
        }
        String mediaType = resolveMediaType(image);

        ReceiptScanResult result = claudeReceiptClient.scan(mediaType, encode(image))
                .orElseThrow(() -> new CustomException(ErrorMessage.RECEIPT_SCAN_FAILED));

        List<ReceiptItemResponse> items = result.items() == null
                ? List.of()
                : result.items().stream().map(this::toResponse).toList();

        return new ReceiptScanResponse(items);
    }

    private ReceiptItemResponse toResponse(ReceiptScanResult.ScannedItem item) {
        Long matchedIngredientId = ingredientService.matchByName(item.name())
                .map(Ingredient::getId)
                .orElse(null);
        Long matchedCategoryId = item.categoryNameGuess() == null
                ? null
                : categoryRepository.findByName(item.categoryNameGuess()).map(Category::getId).orElse(null);
        return new ReceiptItemResponse(item, matchedIngredientId, matchedCategoryId);
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
}
