package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** 기본 식재료 카테고리 중 아직 없는 것만 채워 넣는다. 이미 같은 이름의 카테고리가 있으면 건너뛴다. */
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
        DEFAULT_CATEGORIES.stream()
                .filter(name -> categoryRepository.findByName(name).isEmpty())
                .forEach(name -> categoryRepository.save(Category.builder().name(name).build()));
    }
}
