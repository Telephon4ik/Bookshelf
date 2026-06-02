package com.bookshelf.util;

/**
 * Утилитарный класс для работы с ISBN (International Standard Book Number).
 * <p>
 * Предоставляет методы для нормализации и валидации ISBN-10 и ISBN-13.
 * </p>
 * <p>
 * ISBN-10: состоит из 9 цифр + контрольная цифра (цифра или X).
 * ISBN-13: состоит из 13 цифр.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
public class IsbnUtils {

    /**
     * Нормализует ISBN, удаляя дефисы, пробелы и приводя к верхнему регистру.
     *
     * @param isbn исходная строка ISBN
     * @return нормализованная строка ISBN или null, если входная строка null
     */
    public static String normalize(String isbn) {
        if (isbn == null) return null;
        return isbn.replaceAll("[\\s-]", "").toUpperCase();
    }

    /**
     * Проверяет корректность формата ISBN.
     * Поддерживаются ISBN-10 (10 цифр, последняя может быть X) и ISBN-13 (13 цифр).
     *
     * @param isbn строка для проверки
     * @return true, если ISBN имеет корректный формат, иначе false
     */
    public static boolean isValid(String isbn) {
        if (isbn == null || isbn.isEmpty()) return false;

        String cleanIsbn = normalize(isbn);

        // ISBN-10: 9 цифр + контрольная цифра (цифра или X)
        if (cleanIsbn.length() == 10) {
            return cleanIsbn.matches("^\\d{9}[\\dX]$");
        }
        // ISBN-13: 13 цифр
        else if (cleanIsbn.length() == 13) {
            return cleanIsbn.matches("^\\d{13}$");
        }

        return false;
    }
}