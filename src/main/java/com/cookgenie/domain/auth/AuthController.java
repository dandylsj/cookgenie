package com.cookgenie.domain.auth;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.auth.dto.GoogleLoginRequest;
import com.cookgenie.domain.auth.dto.KakaoLoginRequest;
import com.cookgenie.domain.auth.dto.LoginRequest;
import com.cookgenie.domain.auth.dto.NicknameUpdateRequest;
import com.cookgenie.domain.auth.dto.RefreshTokenReissueRequest;
import com.cookgenie.domain.auth.dto.SignupRequest;
import com.cookgenie.domain.auth.dto.TokenResponse;
import com.cookgenie.domain.auth.dto.UserInfoResponse;
import com.cookgenie.domain.auth.dto.WithdrawRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입/로그인/로그아웃/탈퇴/토큰 재발급/프로필 조회 API. */
@Tag(name = "인증(Auth)", description = "회원가입, 로그인, 로그아웃, 탈퇴, 토큰 재발급 등 인증 관련 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /auth/sign - 회원가입 */
    @Operation(summary = "회원가입", description = "아이디/비밀번호/이메일/닉네임으로 새 계정을 만들고, 즉시 access/refresh 토큰을 발급합니다.")
    @PostMapping("/sign")
    public ResponseEntity<GlobalResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.signup(request)));
    }

    /** POST /auth/guest - 게스트로 시작 */
    @Operation(
            summary = "게스트로 시작",
            description = "회원가입 없이 바로 쓸 수 있는 임시 계정을 만들고 access/refresh 토큰을 발급합니다. "
                    + "냉장고/재료 등록 등 일반 회원과 동일하게 이용할 수 있지만, 3일간 정식 회원으로 전환하지 않으면 "
                    + "계정과 데이터가 자동으로 삭제됩니다. GET /auth/profile 응답의 isGuest/guestExpiresAt으로 "
                    + "남은 기간을 안내해주세요."
    )
    @PostMapping("/guest")
    public ResponseEntity<GlobalResponse<TokenResponse>> createGuest() {
        return ResponseEntity.ok(GlobalResponse.success(authService.createGuestAccount()));
    }

    /** POST /auth/guest/upgrade - 게스트 계정을 정식 회원으로 전환 */
    @Operation(
            summary = "게스트 → 정식 회원 전환",
            description = "게스트 계정에 아이디/비밀번호/이메일/닉네임을 설정해서 정식 회원으로 전환합니다. "
                    + "동일한 계정(유저 id)을 그대로 쓰기 때문에 게스트로 등록해둔 냉장고/재료 데이터가 이관 없이 유지됩니다."
    )
    @PostMapping("/guest/upgrade")
    public ResponseEntity<GlobalResponse<TokenResponse>> upgradeGuest(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.upgradeGuest(resolveToken(accessToken), request)));
    }

    /** POST /auth/login - 로그인 */
    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하여 access/refresh 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<GlobalResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.login(request)));
    }

    /** POST /auth/kakao - 카카오 로그인 (인가 코드 방식) */
    @Operation(
            summary = "카카오 로그인",
            description = "카카오 인가 코드(code)와 그 코드를 발급받을 때 쓴 redirectUri를 받아 카카오 액세스 토큰으로 "
                    + "교환하고, 사용자 정보로 기존 계정을 찾거나 새로 만들어 access/refresh 토큰을 발급합니다."
    )
    @PostMapping("/kakao")
    public ResponseEntity<GlobalResponse<TokenResponse>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.kakaoLogin(request)));
    }

    /** POST /auth/google - 구글 로그인 (인가 코드 방식) */
    @Operation(
            summary = "구글 로그인",
            description = "구글 인가 코드(code)와 그 코드를 발급받을 때 쓴 redirectUri를 받아 구글 액세스 토큰으로 "
                    + "교환하고, 사용자 정보로 기존 계정을 찾거나 새로 만들어 access/refresh 토큰을 발급합니다."
    )
    @PostMapping("/google")
    public ResponseEntity<GlobalResponse<TokenResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.googleLogin(request)));
    }

    /** POST /auth/reissue - refreshToken으로 access/refresh 토큰 재발급 */
    @Operation(summary = "토큰 재발급", description = "refreshToken을 검증한 뒤 새 access/refresh 토큰 쌍을 발급합니다 (토큰 로테이션).")
    @PostMapping("/reissue")
    public ResponseEntity<GlobalResponse<TokenResponse>> reissue(@Valid @RequestBody RefreshTokenReissueRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.reissueToken(request)));
    }

    /** GET /auth/profile - 내 프로필 조회 */
    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자 본인의 프로필 정보를 조회합니다.")
    @GetMapping("/profile")
    public ResponseEntity<GlobalResponse<UserInfoResponse>> profile(@RequestHeader("Authorization") String accessToken) {
        return ResponseEntity.ok(GlobalResponse.success(authService.getUserInfo(resolveToken(accessToken))));
    }

    /** PATCH /auth/nickname - 닉네임 변경 */
    @Operation(summary = "닉네임 변경", description = "로그인한 사용자 본인의 닉네임을 변경합니다.")
    @PatchMapping("/nickname")
    public ResponseEntity<GlobalResponse<UserInfoResponse>> updateNickname(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody NicknameUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.updateNickname(resolveToken(accessToken), request)));
    }

    /** POST /auth/logout - 로그아웃 (저장된 refresh token 삭제) */
    @Operation(summary = "로그아웃", description = "서버에 저장된 refreshToken을 삭제합니다. accessToken 자체는 만료 전까지 유효합니다.")
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponse<Void>> logout(@RequestHeader("Authorization") String accessToken) {
        authService.logout(resolveToken(accessToken));
        return ResponseEntity.ok(GlobalResponse.success(null));
    }

    /** DELETE /auth/withdraw - 회원 탈퇴 (비밀번호 재확인 필요) */
    @Operation(summary = "회원 탈퇴", description = "비밀번호를 재확인한 뒤 계정을 탈퇴 처리합니다 (soft delete). 동일 이메일/아이디로 재가입은 불가능합니다.")
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
