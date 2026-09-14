package com.cookgenie.domain.recipe.external;

/** 유튜브 영상 검색/상세 조회 결과. */
public record YoutubeVideo(
        String videoId,
        String title,
        String description,
        String channelTitle,
        String thumbnailUrl,
        String publishedAt
) {
}
