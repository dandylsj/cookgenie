package com.cookgenie.domain.recipe.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RecipeTagId implements Serializable {

    private Long recipeId;
    private Long tagId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecipeTagId that)) {
            return false;
        }
        return Objects.equals(recipeId, that.recipeId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId, tagId);
    }
}
