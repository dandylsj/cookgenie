package com.cookgenie.domain.recipe.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** YouTube Data API v3로 요리 영상을 검색하고, videoId로 상세(제목/설명/채널명)를 조회하는 클라이언트. */
@Slf4j
@Component
public class YoutubeSearchClient {

    /**
     * search.list는 YouTube의 일일 "Search Queries" 쿼터(기본 100회/일)를 갈아먹는 무거운 호출이라,
     * 같은 검색어를 캐싱해서 재호출을 줄인다. 레시피 검색 결과는 시간에 민감하지 않아 12시간 정도는
     * 캐시해도 체감상 문제가 없다.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final RestClient restClient;
    private final String apiKey;
    private final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();

    public YoutubeSearchClient(@Value("${youtube.api-key}") String apiKey) {
        this.restClient = RestClient.create("https://www.googleapis.com/youtube/v3");
        this.apiKey = apiKey;
    }

    /**
     * 검색어로 요리 영상을 찾는다. 같은 검색어+개수 조합은 {@value #CACHE_TTL}만큼 캐시해서 쿼터 소모를 줄인다.
     * 호출에 실패하면(쿼터 초과 등) 만료된 캐시라도 있으면 그걸 대신 돌려주고, 캐시조차 없으면 빈 목록을 반환한다.
     */
    public List<YoutubeVideo> search(String query, int maxResults) {
        String cacheKey = query.trim() + "|" + maxResults;
        CacheEntry cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.videos();
        }

        try {
            SearchListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("relevanceLanguage", "ko")
                            .queryParam("maxResults", maxResults)
                            .queryParam("q", query)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(SearchListResponse.class);

            List<YoutubeVideo> videos = response == null || response.items() == null
                    ? List.of()
                    : response.items().stream()
                            .filter(item -> item.id() != null && item.snippet() != null)
                            .map(item -> toVideo(item.id().videoId(), item.snippet()))
                            .toList();

            searchCache.put(cacheKey, new CacheEntry(videos, Instant.now()));
            return videos;
        } catch (Exception e) {
            log.warn("[유튜브 영상 검색] 호출 실패 - query={}, error={}", query, e.getMessage());
            return cached != null ? cached.videos() : List.of();
        }
    }

    /** videoId로 영상 상세를 조회한다. 없거나 호출에 실패하면 empty. */
    public Optional<YoutubeVideo> getVideoDetail(String videoId) {
        try {
            VideoListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/videos")
                            .queryParam("part", "snippet")
                            .queryParam("id", videoId)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(VideoListResponse.class);

            if (response == null || response.items() == null) {
                return Optional.empty();
            }
            return response.items().stream()
                    .filter(item -> item.snippet() != null)
                    .map(item -> toVideo(item.id(), item.snippet()))
                    .findFirst();
        } catch (Exception e) {
            log.warn("[유튜브 영상 상세 조회] 호출 실패 - videoId={}, error={}", videoId, e.getMessage());
            return Optional.empty();
        }
    }

    private YoutubeVideo toVideo(String videoId, Snippet snippet) {
        String thumbnailUrl = snippet.thumbnails() != null && snippet.thumbnails().medium() != null
                ? snippet.thumbnails().medium().url()
                : null;
        return new YoutubeVideo(videoId, snippet.title(), snippet.description(), snippet.channelTitle(),
                thumbnailUrl, snippet.publishedAt());
    }

    private record CacheEntry(List<YoutubeVideo> videos, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(CACHE_TTL));
        }
    }

    /** search.list 응답: id가 {kind, videoId} 객체로 내려온다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchListResponse(List<SearchItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchItem(VideoIdHolder id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoIdHolder(String videoId) {
    }

    /** videos.list 응답: id가 videoId 문자열 그대로 내려온다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoListResponse(List<VideoItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VideoItem(String id, Snippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Snippet(String title, String description, String channelTitle, String publishedAt,
                            Thumbnails thumbnails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Thumbnails(Thumbnail medium) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Thumbnail(String url) {
    }
}
