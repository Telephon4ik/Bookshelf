package com.bookshelf.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Класс, представляющий опцию для выпадающего списка.
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class SelectOption {
    /**
     * Значение опции.
     */
    private String value;

    /**
     * Отображаемая метка опции.
     */
    private String label;

    /**
     * Путь к иконке опции.
     */
    private String iconPath;

    /**
     * Признак выбранной опции.
     */
    private boolean selected;

    public SelectOption(String value, String label, String iconPath) {
        this(value, label, iconPath, false);
    }
}