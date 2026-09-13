package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.dto.CategoryResponse;
import com.cookgenie.domain.ingredient.dto.IngredientCreateRequest;
import com.cookgenie.domain.ingredient.dto.IngredientResponse;
import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 식재료 마스터(Ingredient) 조회/검색/등록을 담당하는 서비스. 냉장고 재료(FridgeItem)가 참조하는 식재료 카탈로그. */
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final CategoryRepository categoryRepository;

    /** 이름에 keyword가 포함된 식재료를 검색한다. keyword가 없으면 전체 목록을 반환한다. */
    @Transactional(readOnly = true)
    public List<IngredientResponse> searchIngredients(String keyword) {
        List<Ingredient> ingredients = (keyword == null || keyword.isBlank())
                ? ingredientRepository.findAll()
                : ingredientRepository.findByNameContaining(keyword);
        return ingredients.stream().map(IngredientResponse::new).toList();
    }

    /** 식재료 카테고리 전체 목록 조회. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream().map(CategoryResponse::new).toList();
    }

    /** 목록에 없는 식재료를 사용자가 직접 등록한다. 같은 이름의 카테고리가 있으면 재사용하고, 없으면 새로 만든다. */
    @Transactional
    public IngredientResponse createIngredient(IngredientCreateRequest request) {
        Category category = categoryRepository.findByName(request.getCategoryName())
                .orElseGet(() -> categoryRepository.save(
                        Category.builder().name(request.getCategoryName()).build()));

        Ingredient ingredient = Ingredient.builder()
                .name(request.getName())
                .category(category)
                .ingredientType(IngredientType.RAW)
                .defaultUnit(request.getDefaultUnit())
                .dataSource(DataSource.USER_INPUT)
                .isVerified(false)
                .build();

        return new IngredientResponse(ingredientRepository.save(ingredient));
    }
}
