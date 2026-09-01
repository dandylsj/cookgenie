package com.cookgenie.domain.auth;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.auth.dto.LoginRequest;
import com.cookgenie.domain.auth.dto.RefreshTokenReissueRequest;
import com.cookgenie.domain.auth.dto.SignupRequest;
import com.cookgenie.domain.auth.dto.TokenResponse;
import com.cookgenie.domain.auth.dto.UserInfoResponse;
import com.cookgenie.domain.auth.dto.WithdrawRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입/로그인/로그아웃/탈퇴/토큰 재발급/프로필 조회 API. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /auth/sign - 회원가입 */
    @PostMapping("/sign")
    public ResponseEntity<GlobalResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.signup(request)));
    }

    /** POST /auth/login - 로그인 */
    @PostMapping("/login")
    public ResponseEntity<GlobalResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.login(request)));
    }

    /** POST /auth/reissue - refreshToken으로 access/refresh 토큰 재발급 */
    @PostMapping("/reissue")
    public ResponseEntity<GlobalResponse<TokenResponse>> reissue(@Valid @RequestBody RefreshTokenReissueRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.reissueToken(request)));
    }

    /** GET /auth/profile - 내 프로필 조회 */
    @GetMapping("/profile")
    public ResponseEntity<GlobalResponse<UserInfoResponse>> profile(@RequestHeader("Authorization") String accessToken) {
        return ResponseEntity.ok(GlobalResponse.success(authService.getUserInfo(resolveToken(accessToken))));
    }

    /** POST /auth/logout - 로그아웃 (저장된 refresh token 삭제) */
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponse<Void>> logout(@RequestHeader("Authorization") String accessToken) {
        authService.logout(resolveToken(accessToken));
        return ResponseEntity.ok(GlobalResponse.success(null));
    }

    /** DELETE /auth/withdraw - 회원 탈퇴 (비밀번호 재확인 필요) */
    @DeleteMapping("/withdraw")
    public ResponseEntity<GlobalResponse<Void>> withdraw(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody WithdrawRequest request) {
        authService.withdraw(resolveToken(accessToken), request);
        return ResponseEntity.ok(GlobalResponse.success(null));
    }

    /** Authorization 헤더에서 "Bearer " 접두사를 떼어낸다. */
    private String resolveToken(String accessToken) {
        return accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
    }
}
