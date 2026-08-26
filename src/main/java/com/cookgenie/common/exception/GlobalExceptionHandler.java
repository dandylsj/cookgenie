package com.cookgenie.common.exception;

import com.cookgenie.common.model.response.GlobalResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GlobalResponse.fail(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GlobalResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GlobalResponse.fail(message));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<GlobalResponse<Void>> handleCustomException(CustomException ex) {
        log.warn("CustomException: {}", ex.getMessage());
        return ResponseEntity
                .status(ex.getErrorMessage().getStatus())
                .body(GlobalResponse.fail(ex.getErrorMessage().getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GlobalResponse.fail("잘못된 요청입니다."));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<GlobalResponse<Void>> handleExpiredJwtException(ExpiredJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(GlobalResponse.fail("토큰이 만료되었습니다."));
    }

    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMalformedJwtException(MalformedJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(GlobalResponse.fail("유효하지 않은 토큰입니다."));
    }

    @ExceptionHandler(UnsupportedJwtException.class)
    public ResponseEntity<GlobalResponse<Void>> handleUnsupportedJwtException(UnsupportedJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(GlobalResponse.fail("지원하지 않는 토큰입니다."));
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<GlobalResponse<Void>> handleSignatureException(SignatureException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(GlobalResponse.fail("토큰 서명이 유효하지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GlobalResponse.fail("서버 오류가 발생했습니다."));
    }
}
