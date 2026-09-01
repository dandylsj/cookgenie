package com.cookgenie.domain.fridge;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.dto.FridgeCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeResponse;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeMember;
import com.cookgenie.domain.fridge.entity.FridgeRole;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeMemberRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 냉장고(Fridge) 자체의 생성/조회/삭제를 담당하는 서비스. 재료(FridgeItem) 관리는 {@link FridgeItemService} 참고. */
@Service
@RequiredArgsConstructor
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FridgeItemRepository fridgeItemRepository;
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
        fridgeMemberRepository.deleteByFridgeId(fridgeId);
        fridgeRepository.delete(member.getFridge());
    }
}
