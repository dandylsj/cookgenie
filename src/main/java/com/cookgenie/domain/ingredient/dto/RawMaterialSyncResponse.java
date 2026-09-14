package com.cookgenie.domain.ingredient.dto;

import lombok.Getter;

@Getter
public class RawMaterialSyncResponse {

    private final int totalFetched;
    private final int created;
    private final int updated;
    private final int failed;

    public RawMaterialSyncResponse(int totalFetched, int created, int updated, int failed) {
        this.totalFetched = totalFetched;
        this.created = created;
        this.updated = updated;
        this.failed = failed;
    }
}
