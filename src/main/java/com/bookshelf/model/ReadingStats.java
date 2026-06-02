package com.bookshelf.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Сущность для хранения статистики чтения пользователя.
 * <p>
 * Содержит информацию о количестве прочитанных страниц и завершенных книг
 * за определенную дату.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Entity
@Data
@NoArgsConstructor
public class ReadingStats {
    /**
     * Уникальный идентификатор записи статистики.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Дата, за которую сохраняется статистика. Не может быть null.
     */
    @Column(nullable = false)
    private LocalDate date;

    /**
     * Количество прочитанных страниц. Не может быть null.
     */
    @Column(nullable = false)
    private Integer pagesRead;

    /**
     * Количество завершенных книг. Не может быть null.
     */
    @Column(nullable = false)
    private Integer booksCompleted;

    /**
     * Дополнительные заметки к статистике.
     */
    private String notes;

    /**
     * Выполняет валидацию данных перед сохранением или обновлением в базе данных.
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        if (date == null) {
            date = LocalDate.now();
        }

        if (pagesRead == null) {
            pagesRead = 0;
        }

        if (booksCompleted == null) {
            booksCompleted = 0;
        }

        if (pagesRead < 0) {
            throw new IllegalArgumentException("Количество страниц не может быть отрицательным");
        }

        if (booksCompleted < 0) {
            throw new IllegalArgumentException("Количество книг не может быть отрицательным");
        }

        if (pagesRead > 10000) {
            throw new IllegalArgumentException("Слишком большое количество страниц (максимум 10000)");
        }

        if (booksCompleted > 100) {
            throw new IllegalArgumentException("Слишком большое количество книг (максимум 100)");
        }
    }

    /**
     * Конструктор для удобного создания записи статистики.
     *
     * @param date           дата чтения
     * @param pagesRead      количество прочитанных страниц
     * @param booksCompleted количество завершенных книг
     */
    public ReadingStats(LocalDate date, Integer pagesRead, Integer booksCompleted) {
        this.date = date;
        this.pagesRead = pagesRead;
        this.booksCompleted = booksCompleted;
    }
}