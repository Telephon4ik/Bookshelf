package com.bookshelf.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Сущность, представляющая книгу в библиотеке.
 * <p>
 * Содержит всю информацию о книге: название, автора, жанр, ISBN, статус,
 * рейтинг, теги, обложку, описание и другие метаданные.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book", indexes = {
        @Index(name = "idx_title", columnList = "title"),
        @Index(name = "idx_author", columnList = "author"),
        @Index(name = "idx_genre", columnList = "genre"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_added_date", columnList = "added_date")
})
public class Book {
    /**
     * Уникальный идентификатор книги.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название книги. Не может быть null.
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Автор книги. Не может быть null.
     */
    @Column(nullable = false, length = 100)
    private String author;

    /**
     * Жанр книги.
     */
    @Column(length = 50)
    private String genre;

    /**
     * Международный стандартный книжный номер (ISBN). Должен быть уникальным.
     */
    @Column(unique = true, length = 20)
    private String isbn;

    /**
     * Тип книги (бумажная, электронная, аудиокнига).
     */
    @Enumerated(EnumType.STRING)
    private BookType type;

    /**
     * Путь к файлу книги (для электронных версий).
     */
    private String filePath;

    /**
     * Формат файла (PDF, EPUB, MP3 и т.д.).
     */
    @Column(length = 20)
    private String format;

    /**
     * Статус чтения книги (прочитано/хочу прочитать).
     */
    @Enumerated(EnumType.STRING)
    private BookStatus status;

    /**
     * Рейтинг книги от 0.5 до 5.0 с шагом 0.5.
     */
    private Double rating;

    /**
     * Рецензия пользователя на книгу.
     */
    @Column(length = 2000)
    private String review;

    /**
     * Дата добавления книги в библиотеку.
     */
    @Column(name = "added_date")
    private LocalDateTime addedDate;

    /**
     * Количество страниц в книге.
     */
    private Integer pageCount;

    /**
     * Длительность аудиокниги в минутах.
     */
    private Integer durationMinutes;

    /**
     * Набор тегов для категоризации книги.
     */
    @ElementCollection
    private Set<String> tags = new HashSet<>();

    /**
     * URL изображения обложки книги.
     */
    @Column(length = 500)
    private String coverUrl;

    /**
     * Описание книги.
     */
    @Column(length = 5000)
    private String description;
}