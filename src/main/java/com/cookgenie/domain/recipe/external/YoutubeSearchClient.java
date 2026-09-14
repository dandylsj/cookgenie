package com.cookgenie.domain.recipe.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** YouTube Data API v3로 요리 영상을 검색하고, videoId로 상세(제목/설명/채널명)를 조회하는 클라이언트. */
@Slf4j
@Component
public class YoutubeSearchClient {

    private final RestClient restClient;
    private final String apiKey;

    public YoutubeSearchClient(@Value("${youtube.api-key}") String apiKey) {
        this.restClient = RestClient.create("https://www.googleapis.com/youtube/v3");
        this.apiKey = apiKey;
    }

    /** 검색어로 요리 영상을 찾는다. 호출에 실패하면 빈 목록을 반환한다. */
    public List<YoutubeVideo> search(String query, int maxResults) {
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

            if (response == null || response.items() == null) {
                return List.of();
            }
            return response.items().stream()
                    .filter(item -> item.id() != null && item.snippet() != null)
                    .map(item -> toVideo(item.id().videoId(), item.snippet()))
                    .toList();
        } catch (Exception e) {
            log.warn("[유튜브 영상 검색] 호출 실패 - query={}, error={}", query, e.getMessage());
            return List.of();
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
