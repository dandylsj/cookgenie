package com.cookgenie.domain.fridge;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.domain.fridge.dto.FridgeCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeResponse;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeMember;
import com.cookgenie.domain.fridge.entity.FridgeRole;
import com.cookgenie.domain.fridge.repository.FridgeMemberRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final UserRepository userRepository;

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

    @Transactional(readOnly = true)
    public List<FridgeResponse> getMyFridges(Long userId) {
        return fridgeMemberRepository.findByUserId(userId).stream()
                .map(member -> new FridgeResponse(member.getFridge(), member.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FridgeResponse getFridge(Long userId, Long fridgeId) {
        FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.ACCESS_DENIED));

        return new FridgeResponse(member.getFridge(), member.getRole());
    }
}
