package com.cookgenie.domain.product;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.product.external.ClaudeProductClient;
import com.cookgenie.domain.receipt.ReceiptService;
import com.cookgenie.domain.receipt.dto.ReceiptItemResponse;
import com.cookgenie.domain.receipt.dto.ReceiptScanResponse;
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
 * 실물 상품(포장/라벨) 사진을 Claude에게 분석시켜 식재료 후보 목록을 뽑아주는 서비스. 냉장고/식재료 마스터에
 * 아무것도 쓰지 않는 순수 미리보기이며, 실제 등록은 프론트에서 기존 POST /ingredients + 냉장고 재료 추가
 * API를 사용자가 확인한 항목만 골라 호출한다(영수증/주문내역 인식과 동일한 설계).
 *
 * <p>인식된 이름을 기존 재료/식약처 공식 데이터와 맞춰보는 매칭 로직은 {@link ReceiptService#toItemResponse}를
 * 그대로 재사용한다 - 입력 방식(영수증/주문내역/실물 사진)이 달라도 "이름으로 맞춰본다"는 로직 자체는
 * 완전히 동일해서 별도로 만들지 않았다.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final FridgeRepository fridgeRepository;
    private final ReceiptService receiptService;
    private final ClaudeProductClient claudeProductClient;

    /** 실물 상품 사진을 분석해서 식재료 후보 목록(미리보기)을 반환한다. */
    @Transactional(readOnly = true)
    public ReceiptScanResponse scanProduct(Long fridgeId, MultipartFile image) {
        if (!fridgeRepository.existsById(fridgeId)) {
            throw new CustomException(ErrorMessage.FRIDGE_NOT_FOUND);
        }
        if (image == null || image.isEmpty()) {
            throw new CustomException(ErrorMessage.IMAGE_REQUIRED);
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new CustomException(ErrorMessage.RECEIPT_IMAGE_TOO_LARGE);
        }
        String mediaType = resolveMediaType(image);

        ReceiptScanResult result = claudeProductClient.scan(mediaType, encode(image))
                .orElseThrow(() -> new CustomException(ErrorMessage.IMAGE_RECOGNITION_FAILED));

        List<ReceiptItemResponse> items = result.items() == null
                ? List.of()
                : result.items().stream().map(receiptService::toItemResponse).toList();
        return new ReceiptScanResponse(items);
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
            throw new CustomException(ErrorMessage.IMAGE_RECOGNITION_FAILED);
        }
    }
}
