package com.cookgenie.domain.auth;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.auth.dto.LoginRequest;
import com.cookgenie.domain.auth.dto.RefreshTokenReissueRequest;
import com.cookgenie.domain.auth.dto.SignupRequest;
import com.cookgenie.domain.auth.dto.TokenResponse;
import com.cookgenie.domain.auth.dto.UserInfoResponse;
import com.cookgenie.domain.auth.entity.RefreshToken;
import com.cookgenie.domain.auth.repository.RefreshTokenRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            if (existing.getProvider() != null) {
                throw new CustomException(ErrorMessage.SOCIAL_LOGIN_ACCOUNT);
            }
            throw new CustomException(ErrorMessage.DUPLICATE_EMAIL);
        });
        userRepository.findByLoginId(request.getLoginId()).ifPresent(existing -> {
            throw new CustomException(ErrorMessage.DUPLICATE_LOGIN_ID);
        });

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

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorMessage.INVALID_PASSWORD);
        }

        return issueTokens(user);
    }

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

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String accessToken) {
        jwtUtil.validateToken(accessToken);
        String loginId = jwtUtil.extractLoginId(accessToken);

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorMessage.USER_NOT_FOUND));

        return new UserInfoResponse(user);
    }

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
