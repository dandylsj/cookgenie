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
 * 공공데이터포털에서 미리 내려받아 리소스로 번들한 원재료성식품 표준데이터 CSV 2개(농촌진흥청 농산물,
 * 해양수산부 수산물)를 읽는다. API 활용신청이 막혀있어서 실시간 호출 대신 파일을 통째로 받아 앱에 포함시켜
 * 두는 방식을 쓴다 - 정부 표준데이터라 자주 바뀌지 않으므로 가끔 파일만 새로 받아 교체하면 된다.
 */
@Slf4j
@Component
public class RawFoodCsvLoader {

    private static final String[] RESOURCE_PATHS = {
            "data/nutrition/mfds_raw_food_crops.csv",
            "data/nutrition/mfds_raw_food_fisheries.csv",
    };

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z가-힣]*)");

    public List<RawFoodCsvRow> loadAll() {
        List<RawFoodCsvRow> rows = new ArrayList<>();
        for (String path : RESOURCE_PATHS) {
            rows.addAll(loadOne(path));
        }
        log.info("[원재료 CSV 로드] 총 {}행", rows.size());
        return rows;
    }

    private List<RawFoodCsvRow> loadOne(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream in = resource.getInputStream()) {
            // UTF-8 BOM이 붙어있으면 첫 헤더 이름이 깨지므로 미리 제거한다.
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
                return rows;
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[원재료 CSV 로드] 실패 - path={}, error={}", resourcePath, e.getMessage());
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
                get(record, "식품세분류명"),
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
