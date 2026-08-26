package com.cookgenie.common.util;

import com.cookgenie.common.exception.CustomException;
import com.cookgenie.common.exception.ErrorMessage;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtUtil {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final long ACCESS_TOKEN_TIME = 7 * 24 * 60 * 60 * 1000L; // 7일
    public static final long REFRESH_TOKEN_TIME = 30L * 24 * 60 * 60 * 1000; // 30일

    @Value("${jwt.secret-key}")
    private String secretKeyString;

    private SecretKey key;
    private JwtParser parser;

    @PostConstruct
    public void init() {
        byte[] bytes = Decoders.BASE64.decode(secretKeyString);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.parser = Jwts.parser()
                .verifyWith(this.key)
                .build();
    }

    public String generateAccessToken(Long userId, String loginId, String nickname) {
        Date now = new Date();
        return Jwts.builder()
                .subject(loginId)
                .claim("userId", userId)
                .claim("nickname", nickname)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_TIME))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_TIME))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public void validateToken(String token) {
        parser.parseSignedClaims(token);
    }

    public void validateRefreshToken(String token) {
        try {
            parser.parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorMessage.EXPIRED_TOKEN);
        } catch (Exception e) {
            throw new CustomException(ErrorMessage.INVALID_REFRESH_TOKEN);
        }
    }

    private Claims extractAllClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public String extractLoginId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }
}
