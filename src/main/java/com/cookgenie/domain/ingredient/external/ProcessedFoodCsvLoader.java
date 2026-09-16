package com.cookgenie.domain.ingredient.external;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털 "전국통합식품영양성분정보(가공식품)" 표준데이터를 대표식품코드 기준으로 이미 한 번 압축한
 * 파일(mfds_processed_food_representative.csv)을 읽는다. 원본 CSV는 브랜드별 개별 상품이 31만 건 넘게
 * 있어서(용량도 130MB에 육박) 그대로 앱에 번들할 수 없다 - 그래서 대표식품코드로 묶어서 270건 정도의
 * 대표값만 미리 추려낸 파생 파일을 대신 커밋해서 쓴다(원본은 로컬 전처리에만 한 번 사용하고 버림).
 *
 * <p>원재료성식품({@link RawFoodCsvLoader})과 컬럼 구조는 같지만, 식품대분류코드/대표식품코드의 번호
 * 체계가 데이터구분(원재료 R vs 가공식품 P)마다 서로 다른 뜻으로 재사용되기 때문에 절대 같은 맵에 섞어서
 * 그룹핑하면 안 된다(예: 원재료의 "01"은 곡류지만 가공식품의 "01"은 과자류·빵류) - 그래서 로더/동기화를
 * 완전히 분리해뒀다.
 */
@Slf4j
@Component
public class ProcessedFoodCsvLoader {

    private static final String RESOURCE_PATH = "data/nutrition/mfds_processed_food_representative.csv";
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z가-힣]*)");

    public List<RawFoodCsvRow> loadAll() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStream in = resource.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceFirst("^﻿", "");
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();
            try (CSVParser parser = CSVParser.parse(content, format)) {
                List<RawFoodCsvRow> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    RawFoodCsvRow row = toRow(record);
                    if (row != null) {
                        rows.add(row);
                    }
                }
                log.info("[가공식품 CSV 로드] 총 {}행", rows.size());
                return rows;
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[가공식품 CSV 로드] 실패 - error={}", e.getMessage());
            return List.of();
        }
    }

    private RawFoodCsvRow toRow(CSVRecord record) {
        String representativeName = get(record, "대표식품명");
        if (representativeName == null) {
            return null;
        }
        String[] referenceAmountUnit = parseReference(get(record, "영양성분함량기준량"));
        return new RawFoodCsvRow(
                get(record, "대표식품코드"),
                representativeName,
                get(record, "식품대분류명"),
                null, // 가공식품 표준데이터에는 조리상태 구분이 없다.
                parseInt(referenceAmountUnit[0]),
                referenceAmountUnit[1],
                parseInt(get(record, "에너지(kcal)")),
                parseDecimal(get(record, "탄수화물(g)")),
                parseDecimal(get(record, "단백질(g)")),
                parseDecimal(get(record, "지방(g)")),
                parseDecimal(get(record, "당류(g)")),
                parseDecimal(get(record, "나트륨(mg)")),
                parseDecimal(get(record, "식이섬유(g)"))
        );
    }

    private String get(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String[] parseReference(String raw) {
        if (raw == null) {
            return new String[]{null, "g"};
        }
        Matcher matcher = REFERENCE_PATTERN.matcher(raw);
        if (matcher.find()) {
            String unit = matcher.group(2);
            return new String[]{matcher.group(1), unit.isBlank() ? "g" : unit};
        }
        return new String[]{null, "g"};
    }

    private Integer parseInt(String value) {
        BigDecimal decimal = parseDecimal(value);
        return decimal != null ? decimal.intValue() : null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
