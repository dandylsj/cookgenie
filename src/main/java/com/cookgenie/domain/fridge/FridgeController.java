package com.cookgenie.domain.fridge;

import com.cookgenie.common.model.response.GlobalResponse;
import com.cookgenie.common.util.JwtUtil;
import com.cookgenie.domain.fridge.dto.FridgeCreateRequest;
import com.cookgenie.domain.fridge.dto.FridgeResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fridges")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeService fridgeService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<GlobalResponse<FridgeResponse>> createFridge(
            @RequestHeader("Authorization") String accessToken, @Valid @RequestBody FridgeCreateRequest request) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.createFridge(resolveUserId(accessToken), request)));
    }

    @GetMapping
    public ResponseEntity<GlobalResponse<List<FridgeResponse>>> getMyFridges(
            @RequestHeader("Authorization") String accessToken) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getMyFridges(resolveUserId(accessToken))));
    }

    @GetMapping("/{fridgeId}")
    public ResponseEntity<GlobalResponse<FridgeResponse>> getFridge(
            @RequestHeader("Authorization") String accessToken, @PathVariable Long fridgeId) {
        return ResponseEntity.ok(GlobalResponse.success(fridgeService.getFridge(resolveUserId(accessToken), fridgeId)));
    }

    private Long resolveUserId(String accessToken) {
        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        jwtUtil.validateToken(token);
        return jwtUtil.extractUserId(token);
    }
}
