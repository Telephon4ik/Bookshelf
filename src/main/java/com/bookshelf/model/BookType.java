package com.bookshelf.model;

import lombok.Getter;

/**
 * Перечисление типов книг.
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Getter
public enum BookType {
    /**
     * Бумажная книга.
     */
    PAPER("Бумажная книга", "/icons/type/icon-type-paper.png"),

    /**
     * Электронная книга.
     */
    ELECTRONIC("Электронная книга", "/icons/type/icon-type-electronic.png"),

    /**
     * Аудиокнига.
     */
    AUDIO("Аудиокнига", "/icons/type/icon-type-audio.png");

    /**
     * Отображаемое название типа книги на русском языке.
     */
    private final String displayName;

    /**
     * Путь к иконке типа книги.
     */
    private final String iconPath;

    /**
     * Конструктор типа книги.
     *
     * @param displayName отображаемое название типа
     * @param iconPath путь к иконке
     */
    BookType(String displayName, String iconPath) {
        this.displayName = displayName;
        this.iconPath = iconPath;
    }
}