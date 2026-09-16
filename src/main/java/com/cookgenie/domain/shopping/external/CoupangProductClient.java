package com.cookgenie.domain.shopping.external;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 쿠팡파트너스 Open API(상품검색)로 키워드에 맞는 상품 목록(가격 포함)을 조회하는 클라이언트.
 * 인증은 쿠팡파트너스 고유의 HMAC-SHA256 서명 방식(CEA)을 쓴다 - https://developers.coupangcorp.com
 */
@Slf4j
@Component
public class CoupangProductClient {

    private static final String DOMAIN = "https://api-gateway.coupang.com";
    private static final String SEARCH_PATH = "/v2/providers/affiliate_open_api/apis/openapi/products/search";
    private static final DateTimeFormatter SIGNED_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String accessKey;
    private final String secretKey;
    private final RestClient restClient;

    public CoupangProductClient(
            @Value("${coupang.access-key}") String accessKey,
            @Value("${coupang.secret-key}") String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.restClient = RestClient.builder().baseUrl(DOMAIN).build();
    }

    /** keyword로 상품을 검색해 최대 limit개를 반환한다. 호출 실패(키 미설정/네트워크 오류 등) 시 빈 리스트. */
    public List<CoupangSearchResponse.ProductData> search(String keyword, int limit) {
        String query = "keyword=" + encode(keyword) + "&limit=" + limit;

        try {
            // RestClient의 .uri(String)은 내부적으로 UriComponentsBuilder를 거치면서 이미 퍼센트 인코딩된
            // 문자열을 다시 인코딩(이중 인코딩)해버려서 서명에 쓴 query와 실제 전송되는 query가 달라짐
            // (→ "Invalid signature" 원인). URI.create()로 미리 만든 URI를 그대로 넘겨 재인코딩을 피한다.
            URI uri = URI.create(DOMAIN + SEARCH_PATH + "?" + query);
            CoupangSearchResponse response = restClient.get()
                    .uri(uri)
                    .header("Authorization", generateAuthorization("GET", SEARCH_PATH, query))
                    .retrieve()
                    .body(CoupangSearchResponse.class);

            return response == null || response.data() == null || response.data().productData() == null
                    ? List.of()
                    : response.data().productData();
        } catch (Exception e) {
            log.warn("[쿠팡 상품 검색] 호출 실패 - keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    /** 쿠팡파트너스 서명(Authorization 헤더 값)을 만든다: HMAC-SHA256(secretKey, signedDate+method+path+query). */
    private String generateAuthorization(String method, String path, String query) {
        String signedDate = SIGNED_DATE_FORMAT.format(Instant.now());
        String message = signedDate + method + path + query;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return "CEA algorithm=HmacSHA256, access-key=" + accessKey
                    + ", signed-date=" + signedDate + ", signature=" + signature;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("쿠팡파트너스 API 서명 생성 실패", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
