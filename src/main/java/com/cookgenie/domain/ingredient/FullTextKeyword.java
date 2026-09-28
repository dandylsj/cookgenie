package com.cookgenie.domain.ingredient;

import java.util.Arrays;
import java.util.Optional;

/**
 * 검색어를 FULLTEXT(ngram) BOOLEAN MODE용 따옴표 구문으로 바꾼다.
 *
 * <p>ngram 파서는 글자를 2글자(ngram_token_size 기본값) 단위로 쪼개 색인하므로, "닭가슴살"을 구문("닭가슴살")으로
 * 검색하면 "닭가"·"가슴"·"슴살" 토큰이 연속으로 붙어 있는 행만 나온다 - LIKE '%닭가슴살%'와 같은 결과다.
 * 다만 아래 경우는 FULLTEXT로 LIKE와 똑같은 결과를 보장할 수 없어서 {@link Optional#empty()}를 돌려주고,
 * 호출하는 쪽이 기존 LIKE 검색으로 처리하게 한다(결과가 달라지는 것보다 조금 느린 게 낫다):
 * <ul>
 *   <li>1글자 단어가 섞여 있음("닭", "실온 닭") - 토큰 크기(2)보다 짧아서 색인에 없음</li>
 *   <li>글자/숫자/공백 외의 문자가 섞여 있음 - 괄호·기호는 ngram 토큰화 방식이 LIKE와 달라짐</li>
 * </ul>
 */
public final class FullTextKeyword {

    private static final int NGRAM_TOKEN_SIZE = 2;

    private FullTextKeyword() {
    }

    public static Optional<String> toPhrase(String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        String trimmed = keyword.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == ' ')) {
            return Optional.empty();
        }
        boolean everyTermLongEnough = Arrays.stream(trimmed.split(" "))
                .allMatch(term -> term.codePointCount(0, term.length()) >= NGRAM_TOKEN_SIZE);
        if (!everyTermLongEnough) {
            return Optional.empty();
        }
        return Optional.of("\"" + trimmed + "\"");
    }
}
