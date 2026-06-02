package com.bookshelf.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Утилитарный класс для работы с тегами книг.
 * <p>
 * Предоставляет методы для парсинга строки тегов в набор и форматирования
 * набора тегов в строку.
 * </p>
 * <p>
 * Пример использования:
 * <pre>
 * String tagsInput = "фантастика, классика, детектив";
 * Set&lt;String&gt; tags = TagUtils.parseTags(tagsInput);
 * String formatted = TagUtils.formatTags(tags); // "фантастика, классика, детектив"
 * </pre>
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
public class TagUtils {

    /**
     * Преобразует строку с тегами, разделёнными запятыми, в набор строк.
     * <p>
     * Входная строка разбивается по запятым, каждый тег обрезается от пробелов.
     * Пустые теги и null игнорируются.
     * </p>
     *
     * @param tagsInput строка с тегами (например, "фантастика, классика, детектив")
     * @return набор тегов (уникальных, без лишних пробелов)
     */
    public static Set<String> parseTags(String tagsInput) {
        if (tagsInput == null || tagsInput.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(tagsInput.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Преобразует набор тегов в строку, разделённую запятыми.
     * <p>
     * Теги объединяются через запятую с пробелом.
     * </p>
     *
     * @param tags набор тегов
     * @return строка с тегами, разделёнными запятыми и пробелом,
     *         или пустая строка, если набор null или пуст
     */
    public static String formatTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return String.join(", ", tags);
    }
}