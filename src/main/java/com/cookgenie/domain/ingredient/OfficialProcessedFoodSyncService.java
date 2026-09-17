package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import com.cookgenie.domain.ingredient.external.MfdsProcessedFoodClient;
import com.cookgenie.domain.ingredient.external.OfficialFoodCandidate;
import com.cookgenie.domain.ingredient.repository.OfficialProcessedFoodRepository;
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
 * 식약처 가공식품 공공데이터 API를 foodNm 필터 없이 페이지 단위로 전부 훑어서(약 59만 건, 1000건/페이지 ≈591페이지)
 * {@link OfficialProcessedFood} 테이블로 복사한다. {@link MfdsProcessedFoodClient}의 foodNm 파라미터는
 * 완전 일치만 지원해서(부분검색 불가, 실측으로 확인됨) 삼성헬스 스타일로 몇 글자만 쳐도 후보가 뜨는 UX를
 * 만들려면 API를 매 키 입력마다 부를 수 없다 - 대신 이 테이블에 한 번 복사해두고 LIKE 검색을 한다.
 *
 * <p>API 순차 호출로 몇 분씩 걸리는 작업이라 {@link #runSync()}는 {@code @Async}로 돌려서 트리거 요청을
 * 즉시 응답하게 한다. 컨트롤러가 {@link #prepareSync}(동기, 이미 있으면 스킵 판단)와 {@link #runSync}
 * (비동기, 실제 수집)를 별도로 호출해야 제대로 비동기로 동작한다 - 같은 빈 안에서 자기 자신을 호출하면
 * {@code @Async} 프록시를 안 타서 동기 호출이 되어버리기 때문.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialProcessedFoodSyncService {

    private static final int PAGE_SIZE = 1000;

    private final MfdsProcessedFoodClient mfdsProcessedFoodClient;
    private final OfficialProcessedFoodRepository officialProcessedFoodRepository;

    /**
     * 이미 데이터가 있으면(force=false) 시작하지 않는다. force=true면 기존 데이터는 그대로 둔 채 전체를
     * 다시 훑어서 누락된 항목만 추가로 저장한다(더 이상 지우지 않음 - {@link #saveBatch}가 이미 저장된
     * 항목을 안전하게 걸러내므로 재실행은 항상 멱등적이다). 결과의 {@code started}가 true일 때만 컨트롤러가
     * {@link #runSync()}를 이어서 호출해야 한다.
     */
    @Transactional(readOnly = true)
    public SyncTriggerResult prepareSync(boolean force) {
        long existing = officialProcessedFoodRepository.count();
        if (existing > 0 && !force) {
            return new SyncTriggerResult(false, existing,
                    "이미 " + existing + "건이 저장되어 있습니다. 누락된 항목을 추가로 채우려면 force=true로 호출하세요.");
        }
        return new SyncTriggerResult(true, existing,
                existing > 0
                        ? "기존 " + existing + "건은 그대로 두고, 전체를 다시 훑어 누락된 항목만 추가로 저장합니다."
                        : "백그라운드로 전체 동기화를 시작했습니다. 약 59만 건이라 몇 분 정도 걸립니다.");
    }

    /** 전체 페이지를 순차적으로 가져와 저장한다. 중간에 페이지 조회가 실패하면 그 지점에서 멈춘다(재시도 없음). */
    @Async
    public void runSync() {
        int pageNo = 1;
        int totalCount = -1;
        int imported = 0;

        while (totalCount < 0 || (long) (pageNo - 1) * PAGE_SIZE < totalCount) {
            MfdsProcessedFoodClient.PageResult page = mfdsProcessedFoodClient.fetchPage(pageNo, PAGE_SIZE);
            if (page.totalCount() <= 0) {
                log.warn("[가공식품 전체 동기화] pageNo={}에서 조회 실패/중단 (지금까지 {}건 저장) - "
                        + "다시 시도하려면 force=true로 sync API를 재호출하세요.", pageNo, imported);
                return;
            }
            totalCount = page.totalCount();
            imported += saveBatch(page.items());
            log.info("[가공식품 전체 동기화] pageNo={} 완료, 누적 {}/{}건", pageNo, imported, totalCount);
            pageNo++;
        }

        log.info("[가공식품 전체 동기화] 완료 - 총 {}건 저장", imported);
    }

    /**
     * foodCd는 "대표식품코드"라 서로 다른 제조사/상품이 같은 값을 공유하는 경우가 흔하다(실측으로 확인됨 -
     * foodCd 하나만으로 중복을 걸러냈더니 59만 건 중 24만5천여 건만 남고 다시 돌려도 더 안 늘어남).
     * 그래서 진짜 같은 항목인지는 (foodCd, foodNm, mfrNm) 조합으로 판단해야 한다 - 저장 전에
     * (1) 같은 배치 안에서의 중복을 먼저 걸러내고, (2) foodCd가 겹치는 기존 항목을 가져와 조합 키까지
     * 비교해서 진짜 중복만 걸러낸 뒤 새 항목만 저장한다.
     */
    private int saveBatch(List<OfficialFoodCandidate> items) {
        Map<String, OfficialFoodCandidate> deduped = new LinkedHashMap<>();
        items.forEach(item -> deduped.put(compositeKey(item.foodCd(), item.foodNm(), item.mfrNm()), item));

        Set<String> foodCds = deduped.values().stream().map(OfficialFoodCandidate::foodCd).collect(Collectors.toSet());
        Set<String> alreadySaved = officialProcessedFoodRepository.findByFoodCdIn(foodCds).stream()
                .map(food -> compositeKey(food.getFoodCd(), food.getFoodNm(), food.getMfrNm()))
                .collect(Collectors.toSet());

        List<OfficialProcessedFood> entities = deduped.entrySet().stream()
                .filter(entry -> !alreadySaved.contains(entry.getKey()))
                .map(entry -> toEntity(entry.getValue()))
                .toList();
        if (!entities.isEmpty()) {
            officialProcessedFoodRepository.saveAll(entities);
        }
        return entities.size();
    }

    private String compositeKey(String foodCd, String foodNm, String mfrNm) {
        return foodCd + "" + foodNm + "" + (mfrNm == null ? "" : mfrNm);
    }

    private OfficialProcessedFood toEntity(OfficialFoodCandidate candidate) {
        return OfficialProcessedFood.builder()
                .foodCd(candidate.foodCd())
                .foodNm(candidate.foodNm())
                .mfrNm(candidate.mfrNm())
                .referenceUnit(candidate.referenceUnit())
                .calories(candidate.calories())
                .carbohydrateG(candidate.carbohydrateG())
                .proteinG(candidate.proteinG())
                .fatG(candidate.fatG())
                .sugarG(candidate.sugarG())
                .sodiumMg(candidate.sodiumMg())
                .fiberG(candidate.fiberG())
                .build();
    }

    public record SyncTriggerResult(boolean started, long previousCount, String message) {
    }
}
