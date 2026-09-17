package com.cookgenie.domain.ingredient;

import com.cookgenie.domain.ingredient.entity.OfficialProcessedFood;
import com.cookgenie.domain.ingredient.external.MfdsProcessedFoodClient;
import com.cookgenie.domain.ingredient.external.OfficialFoodCandidate;
import com.cookgenie.domain.ingredient.repository.OfficialProcessedFoodRepository;
import java.util.List;
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
     * 이미 데이터가 있으면(force=false) 시작하지 않는다. force=true면 기존 데이터를 전부 지우고 처음부터
     * 다시 받을 준비를 한다(중복 방지 - foodCd 유니크 제약과 씨름하는 대신 통째로 비우고 재수집).
     * 결과의 {@code started}가 true일 때만 컨트롤러가 {@link #runSync()}를 이어서 호출해야 한다.
     */
    @Transactional
    public SyncTriggerResult prepareSync(boolean force) {
        long existing = officialProcessedFoodRepository.count();
        if (existing > 0 && !force) {
            return new SyncTriggerResult(false, existing,
                    "이미 " + existing + "건이 저장되어 있습니다. 다시 받으려면 force=true로 호출하세요.");
        }
        if (existing > 0) {
            officialProcessedFoodRepository.deleteAllInBatch();
        }
        return new SyncTriggerResult(true, existing,
                "백그라운드로 전체 동기화를 시작했습니다. 약 59만 건이라 몇 분 정도 걸립니다.");
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
            saveBatch(page.items());
            imported += page.items().size();
            log.info("[가공식품 전체 동기화] pageNo={} 완료, 누적 {}/{}건", pageNo, imported, totalCount);
            pageNo++;
        }

        log.info("[가공식품 전체 동기화] 완료 - 총 {}건 저장", imported);
    }

    private void saveBatch(List<OfficialFoodCandidate> items) {
        List<OfficialProcessedFood> entities = items.stream().map(this::toEntity).toList();
        officialProcessedFoodRepository.saveAll(entities);
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
