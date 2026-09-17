package com.cookgenie.domain.fridge;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.dto.FridgeCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeInviteCodeResponse;
import com.cookgenie.domain.fridge.dto.FridgeMemberResponse;
import com.cookgenie.domain.fridge.dto.FridgeResponse;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeMember;
import com.cookgenie.domain.fridge.entity.FridgeRole;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeMemberRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.shopping.repository.ShoppingItemRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 냉장고(Fridge) 자체의 생성/조회/삭제/초대코드를 담당하는 서비스. 재료(FridgeItem) 관리는 {@link FridgeItemService} 참고. */
@Service
@RequiredArgsConstructor
public class FridgeService {

    private static final int INVITE_CODE_EXPIRY_DAYS = 7;

    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final ShoppingItemRepository shoppingItemRepository;
    private final UserRepository userRepository;

    /** 냉장고 생성. 생성자를 OWNER 역할의 FridgeMember로 함께 등록한다. */
    @Transactional
    public FridgeResponse createFridge(Long userId, FridgeCreateRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        Fridge fridge = fridgeRepository.save(
                Fridge.builder()
                        .name(request.getName())
                        .owner(owner)
                        .build()
        );

        fridgeMemberRepository.save(
                FridgeMember.builder()
                        .fridge(fridge)
                        .user(owner)
                        .role(FridgeRole.OWNER)
                        .joinedAt(LocalDateTime.now())
                        .build()
        );

        return new FridgeResponse(fridge, FridgeRole.OWNER);
    }

    /** 내가 멤버(소유자 포함)로 속한 모든 냉장고 목록 조회. */
    @Transactional(readOnly = true)
    public List<FridgeResponse> getMyFridges(Long userId) {
        return fridgeMemberRepository.findByUserId(userId).stream()
                .map(member -> new FridgeResponse(member.getFridge(), member.getRole()))
                .toList();
    }

    /** 냉장고 단건 조회. 요청자가 해당 냉장고의 멤버가 아니면 접근을 거부한다. */
    @Transactional(readOnly = true)
    public FridgeResponse getFridge(Long userId, Long fridgeId) {
        FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));

        return new FridgeResponse(member.getFridge(), member.getRole());
    }

    /** 냉장고 삭제. OWNER만 삭제할 수 있으며, 소속된 FridgeItem/FridgeMember를 먼저 정리한 뒤 냉장고를 삭제한다. */
    @Transactional
    public void deleteFridge(Long userId, Long fridgeId) {
        FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));

        if (member.getRole() != FridgeRole.OWNER) {
            throw new CustomException(ErrorMessage.ACCESS_DENIED);
        }

        fridgeItemRepository.deleteByFridgeId(fridgeId);
        shoppingItemRepository.deleteByFridgeId(fridgeId);
        fridgeMemberRepository.deleteByFridgeId(fridgeId);
        fridgeRepository.delete(member.getFridge());
    }

    /** 초대코드 발급/재발급. OWNER만 가능하며, 4자리 숫자 코드를 7일 유효기간으로 발급한다(재발급 시 이전 코드는 무효화됨). */
    @Transactional
    public FridgeInviteCodeResponse createInviteCode(Long userId, Long fridgeId) {
        FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));

        if (member.getRole() != FridgeRole.OWNER) {
            throw new CustomException(ErrorMessage.ACCESS_DENIED);
        }

        String inviteCode = generateUniqueInviteCode();
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(INVITE_CODE_EXPIRY_DAYS);
        member.getFridge().updateInviteCode(inviteCode, expiryDate);

        return new FridgeInviteCodeResponse(inviteCode, expiryDate);
    }

    /** 초대코드로 냉장고에 참여한다(MEMBER로 등록). 코드가 없거나 만료됐으면, 이미 참여 중이면 각각 예외. */
    @Transactional
    public FridgeResponse joinFridgeByInviteCode(Long userId, String inviteCode) {
        Fridge fridge = fridgeRepository.findByInviteCodeAndInviteCodeExpiryDateAfter(inviteCode, LocalDateTime.now())
                .orElseThrow(() -> new CustomException(ErrorMessage.INVALID_INVITE_CODE));

        if (fridgeMemberRepository.findByFridgeIdAndUserId(fridge.getId(), userId).isPresent()) {
            throw new CustomException(ErrorMessage.ALREADY_FRIDGE_MEMBER);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        FridgeMember member = fridgeMemberRepository.save(
                FridgeMember.builder()
                        .fridge(fridge)
                        .user(user)
                        .role(FridgeRole.MEMBER)
                        .joinedAt(LocalDateTime.now())
                        .build()
        );

        return new FridgeResponse(fridge, member.getRole());
    }

    /** 냉장고 멤버 목록 조회. 소유자가 먼저 오고 그다음 참여일 순으로 정렬한다. 요청자가 멤버가 아니면 접근을 거부한다. */
    @Transactional(readOnly = true)
    public List<FridgeMemberResponse> getMembers(Long userId, Long fridgeId) {
        fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));

        return fridgeMemberRepository.findByFridgeId(fridgeId).stream()
                .sorted(Comparator
                        .comparing((FridgeMember m) -> m.getRole() == FridgeRole.OWNER ? 0 : 1)
                        .thenComparing(FridgeMember::getJoinedAt))
                .map(FridgeMemberResponse::new)
                .toList();
    }

    /** 멤버 강퇴. OWNER만 할 수 있고, 자기 자신은 강퇴할 수 없다. */
    @Transactional
    public void kickMember(Long userId, Long fridgeId, Long targetUserId) {
        FridgeMember requester = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));
        if (requester.getRole() != FridgeRole.OWNER) {
            throw new CustomException(ErrorMessage.ACCESS_DENIED);
        }
        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorMessage.CANNOT_KICK_SELF);
        }

        FridgeMember target = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorMessage.FRIDGE_MEMBER_NOT_FOUND));
        fridgeMemberRepository.delete(target);
    }

    /** 냉장고 탈퇴(본인). OWNER는 탈퇴할 수 없다 - 먼저 냉장고를 삭제하거나 다른 멤버에게 소유권을 넘겨야 한다. */
    @Transactional
    public void leaveFridge(Long userId, Long fridgeId) {
        FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));
        if (member.getRole() == FridgeRole.OWNER) {
            throw new CustomException(ErrorMessage.CANNOT_LEAVE_AS_OWNER);
        }

        fridgeMemberRepository.delete(member);
    }

    /** 현재 유효한(만료 안 된) 다른 냉장고의 코드와 겹치지 않는 4자리 숫자 코드를 만든다. */
    private String generateUniqueInviteCode() {
        String code;
        do {
            code = String.format("%04d", new Random().nextInt(10000));
        } while (fridgeRepository.existsByInviteCodeAndInviteCodeExpiryDateAfter(code, LocalDateTime.now()));
        return code;
    }
}
