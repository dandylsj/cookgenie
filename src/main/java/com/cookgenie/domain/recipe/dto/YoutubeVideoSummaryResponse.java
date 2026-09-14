package com.cookgenie.domain.recipe.dto;

import com.cookgenie.domain.recipe.external.YoutubeVideo;
import lombok.Getter;

/** 유튜브 레시피 검색 결과 하나. 아직 저장된 레시피가 아니라, 가져오기(import) 전 미리보기용이다. */
@Getter
public class YoutubeVideoSummaryResponse {

    private final String videoId;
    private final String title;
    private final String description;
    private final String channelTitle;
    private final String thumbnailUrl;
    private final String publishedAt;
    private final String videoUrl;

    public YoutubeVideoSummaryResponse(YoutubeVideo video) {
        this.videoId = video.videoId();
        this.title = video.title();
        this.description = video.description();
        this.channelTitle = video.channelTitle();
        this.thumbnailUrl = video.thumbnailUrl();
        this.publishedAt = video.publishedAt();
        this.videoUrl = "https://www.youtube.com/watch?v=" + video.videoId();
    }
}
