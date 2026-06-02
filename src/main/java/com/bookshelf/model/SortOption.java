package com.bookshelf.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Класс, представляющий опцию сортировки для отображения в пользовательском интерфейсе.
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class SortOption {
    /**
     * Значение параметра сортировки (например, "title,asc").
     */
    private String value;

    /**
     * Отображаемая метка опции сортировки.
     */
    private String label;

    /**
     * Имя файла иконки для данной опции сортировки.
     */
    private String icon;
}