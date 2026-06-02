package com.bookshelf.repository;

import com.bookshelf.model.ReadingStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

/**
 * Репозиторий для выполнения операций с сущностью {@link ReadingStats}.
 * <p>
 * Предоставляет методы для получения статистики чтения за определенные периоды.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Repository
public interface ReadingStatsRepository extends JpaRepository<ReadingStats, Long> {
    /**
     * Находит записи статистики за указанный период.
     *
     * @param start начальная дата периода
     * @param end   конечная дата периода
     * @return список записей статистики
     */
    List<ReadingStats> findByDateBetween(LocalDate start, LocalDate end);

    /**
     * Возвращает общее количество страниц, прочитанных за указанный период.
     *
     * @param start начальная дата периода
     * @param end   конечная дата периода
     * @return суммарное количество страниц или null, если данных нет
     */
    @Query("SELECT SUM(r.pagesRead) FROM ReadingStats r WHERE r.date BETWEEN :start AND :end")
    Integer sumPagesReadBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * Возвращает общее количество книг, завершенных за указанный период.
     *
     * @param start начальная дата периода
     * @param end   конечная дата периода
     * @return суммарное количество книг или null, если данных нет
     */
    @Query("SELECT SUM(r.booksCompleted) FROM ReadingStats r WHERE r.date BETWEEN :start AND :end")
    Integer sumBooksCompletedBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}