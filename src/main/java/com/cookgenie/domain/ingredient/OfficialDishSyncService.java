package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.OfficialDish;
import com.cookgenie.domain.ingredient.external.MfdsDishClient;
import com.cookgenie.domain.ingredient.external.OfficialDishCandidate;
import com.cookgenie.domain.ingredient.repository.OfficialFoodBulkInsertRepository;
import com.cookgenie.domain.ingredient.repository.OfficialDishRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 식약처 "음식" 공공데이터 API를 foodNm 필터 없이 페이지 단위로 전부 훑어서 {@link OfficialDish} 테이블로
 * 복사한다. {@link com.cookgenie.domain.ingredient.OfficialProcessedFoodSyncService}(가공식품)와 같은
 * 구조 - foodCd 완전 일치만 지원하는 API 특성상 부분검색을 하려면 로컬로 미리 복사해둬야 한다.
 *
 * <p>{@link #runSync()}는 {@code @Async}라 컨트롤러가 동기 체크({@link #prepareSync})와 비동기 실행
 * ({@link #runSync})을 별도로 호출해야 한다 - 같은 빈 안에서 자기 자신을 호출하면 프록시를 안 타서
 * 동기 호출이 되어버리기 때문.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialDishSyncService {

    private static final int PAGE_SIZE = 1000;

    private final MfdsDishClient mfdsDishClient;
    private final OfficialDishRepository officialDishRepository;
    private final OfficialFoodBulkInsertRepository officialFoodBulkInsertRepository;

    /**
     * 이미 데이터가 있으면(force=false) 시작하지 않는다. force=true면 기존 데이터는 그대로 둔 채 전체를
     * 다시 훑어서 누락된 항목만 추가로 저장한다 - {@link #saveBatch}가 이미 저장된 항목을 (foodCd, foodNm,
     * restNm) 조합으로 안전하게 걸러내므로 재실행은 항상 멱등적이다.
     */
    @Transactional(readOnly = true)
    public SyncTriggerResult prepareSync(boolean force) {
        long existing = officialDishRepository.count();
        if (existing > 0 && !force) {
            return new SyncTriggerResult(false, existing,
                    "이미 " + existing + "건이 저장되어 있습니다. 누락된 항목을 추가로 채우려면 force=true로 호출하세요.");
        }
        return new SyncTriggerResult(true, existing,
                existing > 0
                        ? "기존 " + existing + "건은 그대로 두고, 전체를 다시 훑어 누락된 항목만 추가로 저장합니다."
                        : "백그라운드로 전체 동기화를 시작했습니다. 몇 분 정도 걸릴 수 있습니다.");
    }

    /** 전체 페이지를 순차적으로 가져와 저장한다. 중간에 페이지 조회가 실패하면 그 지점에서 멈춘다(재시도 없음). */
    @Async
    public void runSync() {
        int pageNo = 1;
        int totalCount = -1;
        int imported = 0;

        while (totalCount < 0 || (long) (pageNo - 1) * PAGE_SIZE < totalCount) {
            MfdsDishClient.PageResult page = mfdsDishClient.fetchPage(pageNo, PAGE_SIZE);
            if (page.totalCount() <= 0) {
                log.warn("[음식 전체 동기화] pageNo={}에서 조회 실패/중단 (지금까지 {}건 저장) - "
                        + "다시 시도하려면 force=true로 sync API를 재호출하세요.", pageNo, imported);
                return;
            }
            totalCount = page.totalCount();
            imported += saveBatch(page.items());
            log.info("[음식 전체 동기화] pageNo={} 완료, 누적 {}/{}건", pageNo, imported, totalCount);
            pageNo++;
        }

        log.info("[음식 전체 동기화] 완료 - 총 {}건 저장", imported);
    }

    /**
     * foodCd만으로 중복을 걸러내면 가공식품에서 겪은 것과 같은 종류의 버그(서로 다른 항목이 같은 코드를
     * 공유해서 오인 삭제/스킵)를 반복할 수 있어서, (foodCd, foodNm, restNm) 조합으로 판단한다.
     */
    private int saveBatch(List<OfficialDishCandidate> items) {
        Map<String, OfficialDishCandidate> deduped = new LinkedHashMap<>();
        items.forEach(item -> deduped.put(compositeKey(item.foodCd(), item.foodNm(), item.restNm()), item));

        Set<String> foodCds = deduped.values().stream().map(OfficialDishCandidate::foodCd).collect(Collectors.toSet());
        Set<String> alreadySaved = officialDishRepository.findByFoodCdIn(foodCds).stream()
                .map(dish -> compositeKey(dish.getFoodCd(), dish.getFoodNm(), dish.getRestNm()))
                .collect(Collectors.toSet());

        List<OfficialDishCandidate> newItems = deduped.entrySet().stream()
                .filter(entry -> !alreadySaved.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        // IDENTITY PK라 JPA saveAll은 INSERT를 한 건씩 보낸다 - 대량 적재는 JDBC 배치로 한 번에 보낸다.
        officialFoodBulkInsertRepository.insertDishes(newItems);
        return newItems.size();
    }

    private String compositeKey(String foodCd, String foodNm, String restNm) {
        return foodCd + "" + foodNm + "" + (restNm == null ? "" : restNm);
    }

    public record SyncTriggerResult(boolean started, long previousCount, String message) {
    }
}
