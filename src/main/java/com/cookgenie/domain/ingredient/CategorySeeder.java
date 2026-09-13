package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** 카테고리 테이블이 비어 있을 때 기본 식재료 카테고리를 채워 넣는다. */
@Component
@RequiredArgsConstructor
public class CategorySeeder implements CommandLineRunner {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "육류", "해산물", "달걀·두부·콩", "채소", "과일", "유제품",
            "곡류·떡·빵", "면류", "가공식품", "양념·소스", "간식·디저트", "음료·주류"
    );

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            return;
        }
        DEFAULT_CATEGORIES.forEach(name ->
                categoryRepository.save(Category.builder().name(name).build()));
    }
}
