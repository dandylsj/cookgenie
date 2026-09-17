package com.cookgenie.domain.fridge;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.fridge.dto.FridgeCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeInviteCodeResponse;
import com.cookgenie.domain.fridge.dto.FridgeJoinRequest;
import com.cookgenie.domain.fridge.dto.FridgeMemberResponse;
import com.cookgenie.domain.fridge.dto.FridgeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "냉장고(Fridge)", description = "냉장고 생성, 내 냉장고 목록/단건 조회, 삭제, 초대코드 발급/참여 API")
@RestController
@RequestMapping("/fridges")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeService fridgeService;
    private final JwtUtil jwtUtil;

    /** POST /fridges - 냉장고 생성 (요청자가 자동으로 OWNER가 됨) */
    @Operation(summary = "냉장고 생성", description = "새 냉장고를 만들고, 요청한 사용자를 OWNER로 자동 등록합니다.")
    @PostMapping
    public ResponseEntity<GlobalResponse<FridgeResponse>> createFridge(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody FridgeCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.createFridge(resolveUserId(accessToken), request)));
    }

    /** GET /fridges - 내가 속한 냉장고 목록 조회 */
    @Operation(summary = "내 냉장고 목록 조회", description = "내가 멤버(소유자 포함)로 속한 모든 냉장고 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<GlobalResponse<List<FridgeResponse>>> getMyFridges(
            @RequestHeader("Authorization") String accessToken) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getMyFridges(resolveUserId(accessToken))));
    }

    /** GET /fridges/{fridgeId} - 냉장고 단건 조회 (멤버만 가능) */
    @Operation(summary = "냉장고 단건 조회", description = "냉장고 상세 정보를 조회합니다. 해당 냉장고의 멤버가 아니면 접근이 거부됩니다.")
    @GetMapping("/{fridgeId}")
    public ResponseEntity<GlobalResponse<FridgeResponse>> getFridge(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getFridge(resolveUserId(accessToken), fridgeId)));
    }

    /** DELETE /fridges/{fridgeId} - 냉장고 삭제 (OWNER만 가능, 소속 재료/멤버도 함께 삭제) */
    @Operation(summary = "냉장고 삭제", description = "냉장고를 삭제합니다. OWNER만 가능하며, 소속된 재료(FridgeItem)와 멤버(FridgeMember)도 함께 삭제됩니다.")
    @DeleteMapping("/{fridgeId}")
    public ResponseEntity<Void> deleteFridge(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        fridgeService.deleteFridge(resolveUserId(accessToken), fridgeId);
        return ResponseEntity.noContent().build();
    }

    /** POST /fridges/{fridgeId}/invite-code - 초대코드 발급 (OWNER만 가능, 재발급 시 기존 코드는 무효화) */
    @Operation(
            summary = "초대코드 발급",
            description = "냉장고에 다른 사람을 초대할 4자리 숫자 코드를 발급합니다(7일간 유효). OWNER만 발급할 수 있고, "
                    + "다시 발급하면 이전 코드는 즉시 무효화됩니다."
    )
    @PostMapping("/{fridgeId}/invite-code")
    public ResponseEntity<GlobalResponse<FridgeInviteCodeResponse>> createInviteCode(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(
                fridgeService.createInviteCode(resolveUserId(accessToken), fridgeId)));
    }

    /** POST /fridges/join - 초대코드로 냉장고 참여 */
    @Operation(
            summary = "초대코드로 냉장고 참여",
            description = "다른 사람에게 받은 4자리 초대코드를 입력해서 그 냉장고에 MEMBER로 참여합니다."
    )
    @PostMapping("/join")
    public ResponseEntity<GlobalResponse<FridgeResponse>> joinFridge(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody FridgeJoinRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(
                fridgeService.joinFridgeByInviteCode(resolveUserId(accessToken), request.getInviteCode())));
    }

    /** GET /fridges/{fridgeId}/members - 냉장고 멤버 목록 조회 (멤버만 가능) */
    @Operation(
            summary = "냉장고 멤버 목록 조회",
            description = "냉장고에 속한 멤버 목록(유저ID/닉네임/역할/참여일)을 조회합니다. "
                    + "소유자가 먼저, 그다음 참여일 순으로 정렬됩니다. 해당 냉장고의 멤버가 아니면 접근이 거부됩니다."
    )
    @GetMapping("/{fridgeId}/members")
    public ResponseEntity<GlobalResponse<List<FridgeMemberResponse>>> getMembers(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getMembers(resolveUserId(accessToken), fridgeId)));
    }

    /** DELETE /fridges/{fridgeId}/members/{userId} - 멤버 강퇴 (OWNER만 가능) */
    @Operation(
            summary = "멤버 강퇴",
            description = "냉장고에서 멤버를 강퇴합니다. OWNER만 할 수 있고, 자기 자신은 강퇴할 수 없습니다."
    )
    @DeleteMapping("/{fridgeId}/members/{userId}")
    public ResponseEntity<Void> kickMember(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId,
            @Parameter(description = "강퇴할 유저 ID") @PathVariable Long userId) {
        fridgeService.kickMember(resolveUserId(accessToken), fridgeId, userId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /fridges/{fridgeId}/leave - 냉장고 탈퇴 (본인) */
    @Operation(
            summary = "냉장고 탈퇴",
            description = "본인이 냉장고 멤버에서 탈퇴합니다. OWNER는 탈퇴할 수 없습니다(먼저 냉장고를 삭제하거나 "
                    + "다른 멤버에게 소유권을 넘겨야 합니다 - 소유권 이전은 아직 지원하지 않습니다)."
    )
    @DeleteMapping("/{fridgeId}/leave")
    public ResponseEntity<Void> leaveFridge(
            @RequestHeader("Authorization") String accessToken,
            @Parameter(description = "냉장고 ID") @PathVariable Long fridgeId) {
        fridgeService.leaveFridge(resolveUserId(accessToken), fridgeId);
        return ResponseEntity.noContent().build();
    }

    /** Authorization 헤더에서 "Bearer " 접두사를 떼어내고 JWT를 검증한 뒤 userId를 추출한다. */
    private Long resolveUserId(String accessToken) {
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        jwtUtil.validateToken(token);
        return jwtUtil.extractUserId(token);
    }
}
