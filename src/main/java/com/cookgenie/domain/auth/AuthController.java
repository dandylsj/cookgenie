package com.cookgenie.domain.auth;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.domain.auth.dto.LoginRequest;
import com.cookgenie.domain.auth.dto.RefreshTokenReissueRequest;
import com.cookgenie.domain.auth.dto.SignupRequest;
import com.cookgenie.domain.auth.dto.TokenResponse;
import com.cookgenie.domain.auth.dto.UserInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sign")
    public ResponseEntity<GlobalResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<GlobalResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.login(request)));
    }

    @PostMapping("/reissue")
    public ResponseEntity<GlobalResponse<TokenResponse>> reissue(@Valid @RequestBody RefreshTokenReissueRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(authService.reissueToken(request)));
    }

    @GetMapping("/profile")
    public ResponseEntity<GlobalResponse<UserInfoResponse>> profile(@RequestHeader("Authorization") String accessToken) {
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        return ResponseEntity.ok(GlobalResponse.success(authService.getUserInfo(token)));
    }
}
