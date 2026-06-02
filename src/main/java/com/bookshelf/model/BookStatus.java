package com.bookshelf.model;

import lombok.Getter;

/**
 * Перечисление возможных статусов книги в библиотеке.
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Getter
public enum BookStatus {
    /**
     * Книга прочитана.
     */
    READ("Прочитано", "/icons/status/icon-status-read.png"),

    /**
     * Книга добавлена в список желаемого прочтения.
     */
    WANT_TO_READ("В планах", "/icons/status/icon-status-planned.png"),

    /**
     * Книга в процессе чтения.
     */
    IN_PROGRESS("В процессе", "/icons/status/icon-status-progress.png");

    /**
     * Отображаемое название статуса на русском языке.
     */
    private final String displayName;

    /**
     * Путь к иконке статуса.
     */
    private final String iconPath;

    /**
     * Конструктор статуса.
     *
     * @param displayName отображаемое название статуса
     * @param iconPath путь к иконке
     */
    BookStatus(String displayName, String iconPath) {
        this.displayName = displayName;
        this.iconPath = iconPath;
    }
}