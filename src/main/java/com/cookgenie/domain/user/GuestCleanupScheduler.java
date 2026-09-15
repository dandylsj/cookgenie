package com.cookgenie.domain.user;

import com.cookgenie.domain.auth.repository.RefreshTokenRepository;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeMemberRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** User.GUEST_RETENTION_DAYS(3일)간 정식 회원으로 전환되지 않은 게스트 계정과 그 냉장고 데이터를 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final FridgeRepository fridgeRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /** 매시 정각에 실행. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredGuests() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(User.GUEST_RETENTION_DAYS);
        List<User> expiredGuests = userRepository.findByProviderAndCreatedAtBefore(User.GUEST_PROVIDER, cutoff);
        if (expiredGuests.isEmpty()) {
            return;
        }

        for (User guest : expiredGuests) {
            for (Fridge fridge : fridgeRepository.findByOwnerId(guest.getId())) {
                fridgeItemRepository.deleteByFridgeId(fridge.getId());
                fridgeMemberRepository.deleteByFridgeId(fridge.getId());
                fridgeRepository.delete(fridge);
            }
            fridgeMemberRepository.deleteAll(fridgeMemberRepository.findByUserId(guest.getId()));
            refreshTokenRepository.deleteByUserId(guest.getId());
            userRepository.delete(guest);
        }

        log.info("[게스트 계정 정리] {}일 경과한 게스트 계정 {}개 삭제", User.GUEST_RETENTION_DAYS, expiredGuests.size());
    }
}
