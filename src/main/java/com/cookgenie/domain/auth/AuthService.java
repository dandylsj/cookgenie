package com.cookgenie.domain.auth;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.auth.dto.LoginRequest;
import com.cookgenie.domain.auth.dto.NicknameUpdateRequest;
import com.cookgenie.domain.auth.dto.RefreshTokenReissueRequest;
import com.cookgenie.domain.auth.dto.SignupRequest;
import com.cookgenie.domain.auth.dto.TokenResponse;
import com.cookgenie.domain.auth.dto.UserInfoResponse;
import com.cookgenie.domain.auth.dto.WithdrawRequest;
import com.cookgenie.domain.auth.entity.RefreshToken;
import com.cookgenie.domain.auth.repository.RefreshTokenRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.entity.UserStatus;
import com.cookgenie.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입/로그인/로그아웃/탈퇴/토큰 재발급/프로필 조회 등 인증 관련 비즈니스 로직. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    /** 회원가입. 이메일/아이디 중복, 소셜 계정 여부를 확인한 뒤 비밀번호를 BCrypt로 해싱해 저장하고 토큰을 발급한다. */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        checkEmailAndLoginIdAvailable(request.getEmail(), request.getLoginId(), null);

        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .nickname(request.getNickname())
                .profileImageUrl(request.getProfileImageUrl())
                .build();
        userRepository.save(user);

        return issueTokens(user);
    }

    /** 게스트로 시작. 회원가입 없이 바로 쓸 수 있도록 임시 계정을 만들고 토큰을 발급한다. 3일간 미전환 시 자동 삭제된다. */
    @Transactional
    public TokenResponse createGuestAccount() {
        String uuid = UUID.randomUUID().toString();
        User user = User.builder()
                .email("guest_" + uuid + "@cookgenie.guest")
                .loginId("guest_" + uuid.substring(0, 12))
                .nickname("게스트")
                .provider(User.GUEST_PROVIDER)
                .providerId(uuid)
                .build();
        userRepository.save(user);

        return issueTokens(user);
    }

    /** 게스트 계정을 정식 회원으로 전환한다. 같은 유저 row를 그대로 쓰므로 기존에 쌓인 냉장고/재료 데이터는 그대로 유지된다. */
    @Transactional
    public TokenResponse upgradeGuest(String accessToken, SignupRequest request) {
        jwtUtil.validateToken(accessToken);
        Long userId = jwtUtil.extractUserId(accessToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));
        if (!user.isGuest()) {
            throw new CustomException(ErrorMessage.NOT_GUEST_ACCOUNT);
        }

        checkEmailAndLoginIdAvailable(request.getEmail(), request.getLoginId(), user.getId());

        user.upgradeFromGuest(
                request.getLoginId(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail(),
                request.getNickname(),
                request.getProfileImageUrl());

        return issueTokens(user);
    }

    /** 이메일/아이디가 다른 사용자에게 이미 쓰이고 있는지 확인한다. selfUserId와 같은 유저가 쓰는 값이면(게스트 전환) 통과시킨다. */
    private void checkEmailAndLoginIdAvailable(String email, String loginId, Long selfUserId) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (selfUserId != null && existing.getId().equals(selfUserId)) {
                return;
            }
            if (existing.getProvider() != null) {
                throw new CustomException(ErrorMessage.SOCIAL_LOGIN_ACCOUNT);
            }
            throw new CustomException(ErrorMessage.DUPLICATE_EMAIL);
        });
        userRepository.findByLoginId(loginId).ifPresent(existing -> {
            if (selfUserId != null && existing.getId().equals(selfUserId)) {
                return;
            }
            throw new CustomException(ErrorMessage.DUPLICATE_LOGIN_ID);
        });
    }

    /** 로그인. 탈퇴 여부와 비밀번호를 확인한 뒤 토큰을 발급한다. */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(ErrorMessage.WITHDRAWN_USER);
        }
        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorMessage.INVALID_PASSWORD);
        }

        return issueTokens(user);
    }

    /** 로그아웃. 서버에 저장된 refresh token을 삭제한다 (access token 자체는 만료 전까지 유효한 JWT 한계 그대로). */
    @Transactional
    public void logout(String accessToken) {
        jwtUtil.validateToken(accessToken);
        Long userId = jwtUtil.extractUserId(accessToken);
        refreshTokenRepository.deleteByUserId(userId);
    }

    /** 회원 탈퇴. 비밀번호 재확인 후 soft delete(status=WITHDRAWN, password 초기화)하고 refresh token을 삭제한다. */
    @Transactional
    public void withdraw(String accessToken, WithdrawRequest request) {
        jwtUtil.validateToken(accessToken);
        String loginId = jwtUtil.extractLoginId(accessToken);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorMessage.INVALID_PASSWORD);
        }

        user.withdraw();
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    /** 토큰 재발급. refresh token을 검증하고 DB에 저장된 값과 일치하는지 확인한 뒤 access/refresh 토큰을 새로 발급한다(로테이션). */
    @Transactional
    public TokenResponse reissueToken(RefreshTokenReissueRequest request) {
        jwtUtil.validateRefreshToken(request.getRefreshToken());
        Long userId = jwtUtil.extractUserId(request.getRefreshToken());

        RefreshToken savedToken = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.INVALID_REFRESH_TOKEN));

        if (!savedToken.getToken().equals(request.getRefreshToken())) {
            throw new CustomException(ErrorMessage.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getLoginId(), user.getNickname());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());

        savedToken.updateToken(newRefreshToken);
        refreshTokenRepository.save(savedToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    /** access token으로 로그인된 사용자의 프로필 정보를 조회한다. */
    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String accessToken) {
        jwtUtil.validateToken(accessToken);
        String loginId = jwtUtil.extractLoginId(accessToken);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        return new UserInfoResponse(user);
    }

    /** 닉네임 변경. 닉네임은 유니크 제약이 없어서 중복 검사는 하지 않는다. */
    @Transactional
    public UserInfoResponse updateNickname(String accessToken, NicknameUpdateRequest request) {
        jwtUtil.validateToken(accessToken);
        Long userId = jwtUtil.extractUserId(accessToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));
        user.updateNickname(request.getNickname());

        return new UserInfoResponse(user);
    }

    /** access/refresh 토큰을 새로 만들고, 기존 refresh token row가 있으면 갱신하며 없으면 새로 저장한다. */
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getLoginId(), user.getNickname());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        RefreshToken refreshTokenEntity = refreshTokenRepository.findByUserId(user.getId())
                .orElse(new RefreshToken(user.getId(), refreshToken));
        refreshTokenEntity.updateToken(refreshToken);
        refreshTokenRepository.save(refreshTokenEntity);

        return new TokenResponse(accessToken, refreshToken);
    }
}
