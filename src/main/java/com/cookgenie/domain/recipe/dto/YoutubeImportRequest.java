package com.cookgenie.domain.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class YoutubeImportRequest {

    @NotBlank(message = "videoId는 필수입니다.")
    private String videoId;
}
