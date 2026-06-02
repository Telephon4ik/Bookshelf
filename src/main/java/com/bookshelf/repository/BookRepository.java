package com.bookshelf.repository;

import com.bookshelf.model.Book;
import com.bookshelf.model.BookStatus;
import com.bookshelf.model.BookType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для выполнения операций с сущностью {@link Book}.
 * <p>
 * Предоставляет методы для поиска, фильтрации и получения статистики по книгам.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByTitleContainingIgnoreCase(String title);
    List<Book> findByAuthorContainingIgnoreCase(String author);
    List<Book> findByGenre(String genre);
    List<Book> findByStatus(BookStatus status);
    List<Book> findByRating(Integer rating);
    List<Book> findByAddedDateBetween(LocalDateTime start, LocalDateTime end);
    List<Book> findByTagsContaining(String tag);

    /**
     * Возвращает средний рейтинг всех книг.
     *
     * @return средний рейтинг или 0, если рейтингов нет
     */
    @Query("SELECT COALESCE(AVG(b.rating), 0) FROM Book b WHERE b.rating IS NOT NULL")
    Double getAverageRating();

    /**
     * Возвращает статистику по жанрам (жанр -> количество книг).
     *
     * @return список массивов объектов [жанр, количество]
     */
    @Query("SELECT b.genre, COUNT(b) FROM Book b WHERE b.genre IS NOT NULL GROUP BY b.genre")
    List<Object[]> getGenreStatisticsRaw();

    /**
     * Возвращает статистику по тегам (тег -> количество книг).
     *
     * @return список массивов объектов [тег, количество]
     */
    @Query("SELECT t, COUNT(b) FROM Book b JOIN b.tags t GROUP BY t")
    List<Object[]> getTagStatisticsRaw();

    Page<Book> findAll(Pageable pageable);
    Optional<Book> findByIsbn(String isbn);

    /**
     * Проверяет существование книги с указанным ISBN, исключая книгу с заданным ID.
     *
     * @param isbn      ISBN для проверки
     * @param excludeId ID книги, которую следует исключить из проверки
     * @return true, если книга с таким ISBN существует, иначе false
     */
    @Query("SELECT COUNT(b) > 0 FROM Book b WHERE b.isbn = :isbn AND b.id != :excludeId")
    boolean existsByIsbnAndIdNot(@Param("isbn") String isbn, @Param("excludeId") Long excludeId);

    /**
     * Выполняет расширенный поиск книг по нескольким критериям.
     *
     * @param title     название книги
     * @param author    автор книги
     * @param genre     жанр книги
     * @param status    статус книги
     * @param minRating минимальный рейтинг
     * @param maxRating максимальный рейтинг
     * @return список книг, соответствующих критериям
     */
    @Query("SELECT b FROM Book b WHERE " +
            "(:title IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
            "(:author IS NULL OR LOWER(b.author) LIKE LOWER(CONCAT('%', :author, '%'))) AND " +
            "(:genre IS NULL OR b.genre = :genre) AND " +
            "(:status IS NULL OR b.status = :status) AND " +
            "(:minRating IS NULL OR b.rating >= :minRating) AND " +
            "(:maxRating IS NULL OR b.rating <= :maxRating)")
    List<Book> advancedSearch(@Param("title") String title,
                              @Param("author") String author,
                              @Param("genre") String genre,
                              @Param("status") BookStatus status,
                              @Param("minRating") Integer minRating,
                              @Param("maxRating") Integer maxRating);

    long countByStatus(BookStatus status);
    long countByType(BookType type);

    /**
     * Возвращает количество книг по годам добавления.
     *
     * @return список массивов объектов [год, количество]
     */
    @Query("SELECT YEAR(b.addedDate), COUNT(b) FROM Book b GROUP BY YEAR(b.addedDate)")
    List<Object[]> countBooksByYear();

    /**
     * Возвращает список всех уникальных жанров.
     *
     * @return список жанров
     */
    @Query("SELECT DISTINCT b.genre FROM Book b WHERE b.genre IS NOT NULL AND b.genre != ''")
    List<String> findAllGenres();

    /**
     * Выполняет поиск книг по названию или автору с пагинацией.
     *
     * @param query    поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с результатами поиска
     */
    @Query("SELECT b FROM Book b WHERE " +
            "LOWER(b.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(b.author) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Book> searchByTitleOrAuthor(@Param("query") String query, Pageable pageable);

    /**
     * Выполняет фильтрацию книг по заданным критериям с пагинацией.
     *
     * @param status    статус книги
     * @param genre     жанр книги
     * @param minRating минимальный рейтинг
     * @param maxRating максимальный рейтинг
     * @param pageable  параметры пагинации
     * @return страница с отфильтрованными книгами
     */
    @Query("SELECT b FROM Book b WHERE " +
            "(:status IS NULL OR b.status = :status) AND " +
            "(:genre IS NULL OR b.genre = :genre) AND " +
            "(:minRating IS NULL OR b.rating >= :minRating) AND " +
            "(:maxRating IS NULL OR b.rating <= :maxRating)")
    Page<Book> filterBooks(@Param("status") BookStatus status,
                           @Param("genre") String genre,
                           @Param("minRating") Integer minRating,
                           @Param("maxRating") Integer maxRating,
                           Pageable pageable);
}