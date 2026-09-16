package com.cookgenie.domain.shopping.external;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
    private final RestClient noRedirectClient;

    public CoupangProductClient(
            @Value("${coupang.access-key}") String accessKey,
            @Value("${coupang.secret-key}") String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.restClient = RestClient.builder().baseUrl(DOMAIN).build();
        // 상품 이미지 URL(리다이렉트 추적 링크)의 실제 CDN 주소를 알아내기 위해 리다이렉트를 따라가지 않는 클라이언트가 별도로 필요함.
        this.noRedirectClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .connectTimeout(Duration.ofSeconds(3))
                                .build()))
                .build();
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

            List<CoupangSearchResponse.ProductData> products =
                    response == null || response.data() == null || response.data().productData() == null
                            ? List.of()
                            : response.data().productData();

            // productImage는 실제 이미지가 아니라 ads-partners.coupang.com의 추적용 리다이렉트 링크라서,
            // 광고/트래커 차단 확장 프로그램이 도메인만 보고 요청 자체를 막아버려 썸네일이 하나도 안 뜨는 문제가 있었음.
            // 여기서 미리 리다이렉트를 한 번 따라가 실제 CDN(image*.coupangcdn.com) 주소로 바꿔서 내려준다.
            return products.parallelStream()
                    .map(p -> new CoupangSearchResponse.ProductData(
                            p.productId(), p.productName(), resolveImageUrl(p.productImage()), p.productPrice(),
                            p.productUrl(), p.isRocket(), p.isFreeShipping(), p.categoryName()))
                    .toList();
        } catch (Exception e) {
            log.warn("[쿠팡 상품 검색] 호출 실패 - keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    /** 추적용 리다이렉트 링크(ads-partners.coupang.com)의 Location 헤더를 따라가 실제 이미지 CDN 주소를 알아낸다. 실패하면 원래 링크 그대로 반환. */
    private String resolveImageUrl(String trackedUrl) {
        if (trackedUrl == null || trackedUrl.isBlank()) {
            return trackedUrl;
        }
        try {
            return noRedirectClient.head()
                    .uri(trackedUrl)
                    .exchange((request, response) -> {
                        URI location = response.getHeaders().getLocation();
                        return location != null ? location.toString() : trackedUrl;
                    });
        } catch (Exception e) {
            log.warn("[쿠팡 이미지 URL 변환] 실패 - url={}, error={}", trackedUrl, e.getMessage());
            return trackedUrl;
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
