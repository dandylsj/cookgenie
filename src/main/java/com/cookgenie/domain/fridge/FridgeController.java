package com.cookgenie.domain.fridge;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.fridge.dto.FridgeCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 냉장고(Fridge) 생성/조회/삭제 API. 냉장고 안의 재료(FridgeItem) API는 {@link FridgeItemController} 참고. */
@RestController
@RequestMapping("/fridges")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeService fridgeService;
    private final JwtUtil jwtUtil;

    /** POST /fridges - 냉장고 생성 (요청자가 자동으로 OWNER가 됨) */
    @PostMapping
    public ResponseEntity<GlobalResponse<FridgeResponse>> createFridge(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody FridgeCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.createFridge(resolveUserId(accessToken), request)));
    }

    /** GET /fridges - 내가 속한 냉장고 목록 조회 */
    @GetMapping
    public ResponseEntity<GlobalResponse<List<FridgeResponse>>> getMyFridges(
            @RequestHeader("Authorization") String accessToken) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getMyFridges(resolveUserId(accessToken))));
    }

    /** GET /fridges/{fridgeId} - 냉장고 단건 조회 (멤버만 가능) */
    @GetMapping("/{fridgeId}")
    public ResponseEntity<GlobalResponse<FridgeResponse>> getFridge(
            @RequestHeader("Authorization") String accessToken, @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getFridge(resolveUserId(accessToken), fridgeId)));
    }

    /** DELETE /fridges/{fridgeId} - 냉장고 삭제 (OWNER만 가능, 소속 재료/멤버도 함께 삭제) */
    @DeleteMapping("/{fridgeId}")
    public ResponseEntity<Void> deleteFridge(
            @RequestHeader("Authorization") String accessToken, @PathVariable Long fridgeId) {
        fridgeService.deleteFridge(resolveUserId(accessToken), fridgeId);
        return ResponseEntity.noContent().build();
    }

    /** Authorization 헤더에서 "Bearer " 접두사를 떼어내고 JWT를 검증한 뒤 userId를 추출한다. */
    private Long resolveUserId(String accessToken) {
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        jwtUtil.validateToken(token);
        return jwtUtil.extractUserId(token);
    }
}
