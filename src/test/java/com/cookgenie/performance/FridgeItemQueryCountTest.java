package com.cookgenie.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.cookgenie.domain.fridge.FridgeItemService;
import com.cookgenie.domain.fridge.dto.FridgeItemResponse;
import com.cookgenie.domain.fridge.entity.Fridge;
import com.cookgenie.domain.fridge.entity.FridgeItem;
import com.cookgenie.domain.fridge.entity.StorageLocation;
import com.cookgenie.domain.fridge.repository.FridgeItemRepository;
import com.cookgenie.domain.fridge.repository.FridgeRepository;
import com.cookgenie.domain.ingredient.entity.Category;
import com.cookgenie.domain.ingredient.entity.DataSource;
import com.cookgenie.domain.ingredient.entity.Ingredient;
import com.cookgenie.domain.ingredient.entity.IngredientType;
import com.cookgenie.domain.ingredient.entity.NutritionInfo;
import com.cookgenie.domain.ingredient.repository.CategoryRepository;
import com.cookgenie.domain.ingredient.repository.IngredientRepository;
import com.cookgenie.domain.ingredient.repository.NutritionInfoRepository;
import com.cookgenie.domain.user.entity.User;
import com.cookgenie.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 냉장고 재료 목록 조회(GET /fridges/{id}/items)의 쿼리 수: 개선 전(N+1) vs 개선 후(fetch join + IN 조회).
 *
 * <p>개선 전 코드는 재료마다 ingredient(이름), category(이름), nutrition_info(영양정보)를 따로 조회해서
 * 재료가 N개면 쿼리가 1 + 3N번 나갔다. 개선 전 로직을 테스트 안에 그대로 재현해서 같은 데이터로 비교한다.
 */
class FridgeItemQueryCountTest extends PerformanceTestSupport {

    private static final int ITEM_COUNT = 50;

    @Autowired
    private FridgeItemService fridgeItemService;
    @Autowired
    private FridgeItemRepository fridgeItemRepository;
    @Autowired
    private FridgeRepository fridgeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private NutritionInfoRepository nutritionInfoRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long fridgeId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.save(User.builder()
                .email("perf_" + unique + "@cookgenie.test").nickname("perf").build());
        Fridge fridge = fridgeRepository.save(Fridge.builder().name("perf-" + unique).owner(owner).build());
        fridgeId = fridge.getId();

        List<FridgeItem> items = new ArrayList<>();
        for (int i = 0; i < ITEM_COUNT; i++) {
            // 재료마다 카테고리가 다르게(실제로는 12개 카테고리에 흩어져 있음) - 영속성 컨텍스트 캐시 효과를 없앤다.
            Category category = categoryRepository.save(Category.builder().name("c" + unique + i).build());
            Ingredient ingredient = ingredientRepository.save(Ingredient.builder()
                    .name("재료" + unique + i).category(category)
                    .ingredientType(IngredientType.RAW).dataSource(DataSource.USER_INPUT).build());
            nutritionInfoRepository.save(NutritionInfo.builder()
                    .ingredient(ingredient).referenceUnit("g").calories(100)
                    .carbohydrateG(BigDecimal.TEN).proteinG(BigDecimal.ONE).fatG(BigDecimal.ONE).build());
            items.add(FridgeItem.builder()
                    .fridge(fridge).ingredient(ingredient).quantity(BigDecimal.valueOf(200)).unit("g")
                    .storageLocation(StorageLocation.REFRIGERATED).expiryDate(LocalDate.now().plusDays(i % 7))
                    .purchasedAt(LocalDate.now().minusDays(i)).build());
        }
        fridgeItemRepository.saveAll(items);
    }

    @Test
    void 냉장고_재료_목록_조회_쿼리수() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        // 개선 전 로직 재현: findByFridgeId → 재료마다 NutritionInfo 단건 조회 + LAZY ingredient/category 접근
        statistics.clear();
        List<FridgeItemResponse> before = transactionTemplate.execute(status ->
                fridgeItemRepository.findByFridgeId(fridgeId).stream()
                        .map(item -> new FridgeItemResponse(item,
                                nutritionInfoRepository.findByIngredientId(item.getIngredient().getId()).orElse(null)))
                        .toList());
        long beforeQueries = statistics.getPrepareStatementCount();

        // 개선 후: 실제 서비스 메서드
        statistics.clear();
        List<FridgeItemResponse> after = fridgeItemService.getItems(fridgeId);
        long afterQueries = statistics.getPrepareStatementCount();

        // 통계 API도 같은 방식으로 개선됐다
        statistics.clear();
        fridgeItemService.getStatistics(fridgeId);
        long statisticsQueries = statistics.getPrepareStatementCount();

        System.out.printf("%n=== 냉장고 재료 목록 조회 쿼리 수 (재료 %d개) ===%n", ITEM_COUNT);
        System.out.printf("개선 전 (N+1)            : %d번%n", beforeQueries);
        System.out.printf("개선 후 (fetch join + IN): %d번%n", afterQueries);
        System.out.printf("통계 API (개선 후)        : %d번%n", statisticsQueries);

        assertThat(after).hasSize(ITEM_COUNT);
        assertThat(after).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(before);
        assertThat(afterQueries).isLessThanOrEqualTo(3);
        assertThat(beforeQueries).isGreaterThan(ITEM_COUNT);
    }
}
