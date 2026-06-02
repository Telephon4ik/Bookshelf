package com.bookshelf.service;

import com.bookshelf.model.Book;
import com.bookshelf.model.BookStatus;
import com.bookshelf.model.BookType;
import com.bookshelf.model.ReadingStats;
import com.bookshelf.repository.BookRepository;
import com.bookshelf.repository.ReadingStatsRepository;
import com.bookshelf.util.IsbnUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервисный слой для управления книгами и статистикой чтения.
 * <p>
 * Содержит бизнес-логику приложения: сохранение, поиск, фильтрацию,
 * валидацию и нормализацию данных книг, а также работу со статистикой.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Service
@Transactional(readOnly = true)
public class BookService {

    private static final Logger logger = LoggerFactory.getLogger(BookService.class);

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReadingStatsRepository statsRepository;

    /**
     * Возвращает список всех книг.
     *
     * @return список всех книг
     */
    public List<Book> getAllBooks() {
        logger.debug("Получение всех книг");
        return bookRepository.findAll();
    }

    /**
     * Возвращает страницу книг с поддержкой пагинации.
     *
     * @param pageable параметры пагинации
     * @return страница книг
     */
    public Page<Book> getAllBooks(Pageable pageable) {
        logger.debug("Получение книг с пагинацией: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return bookRepository.findAll(pageable);
    }

    /**
     * Выполняет поиск книг по названию или автору.
     *
     * @param query    поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с результатами поиска
     */
    public Page<Book> searchBooks(String query, Pageable pageable) {
        logger.debug("Поиск книг по запросу: {}", query);
        if (query == null || query.isEmpty()) {
            return bookRepository.findAll(pageable);
        }
        return bookRepository.searchByTitleOrAuthor(query, pageable);
    }

    /**
     * Выполняет фильтрацию книг по заданным критериям.
     *
     * @param status    статус книги
     * @param genre     жанр книги
     * @param minRating минимальный рейтинг
     * @param maxRating максимальный рейтинг
     * @param pageable  параметры пагинации
     * @return страница с отфильтрованными книгами
     */
    public Page<Book> filterBooks(BookStatus status, String genre,
                                  Integer minRating, Integer maxRating,
                                  Pageable pageable) {
        logger.debug("Фильтрация книг: status={}, genre={}, minRating={}, maxRating={}",
                status, genre, minRating, maxRating);
        return bookRepository.filterBooks(status, genre, minRating, maxRating, pageable);
    }

    /**
     * Возвращает список всех жанров.
     *
     * @return список жанров
     */
    public List<String> getAllGenres() {
        return bookRepository.findAllGenres();
    }

    /**
     * Находит книгу по её идентификатору.
     *
     * @param id идентификатор книги
     * @return объект книги или null, если книга не найдена
     */
    public Book getBookById(Long id) {
        logger.debug("Поиск книги с ID: {}", id);
        return bookRepository.findById(id).orElse(null);
    }

    /**
     * Сохраняет книгу в базе данных с предварительной валидацией и нормализацией данных.
     * <p>
     * При изменении статуса книги на READ автоматически добавляет запись в статистику чтения.
     * </p>
     *
     * @param book объект книги для сохранения
     * @throws IllegalArgumentException если данные книги не проходят валидацию
     */
    @Transactional
    public void saveBook(Book book) {
        logger.info("Сохранение книги: {}", book.getTitle());

        // === ВАЛИДАЦИЯ РЕЙТИНГА ===
        if (book.getRating() != null) {
            double rating = book.getRating();
            if (rating < 0.5 || rating > 5) {
                logger.warn("Некорректный рейтинг: {}", rating);
                throw new IllegalArgumentException("Рейтинг должен быть от 0.5 до 5");
            }
            double roundedRating = Math.round(rating * 2) / 2.0;
            if (roundedRating != rating) {
                book.setRating(roundedRating);
            }
        }

        // === ВАЛИДАЦИЯ КОЛИЧЕСТВА СТРАНИЦ ===
        if (book.getPageCount() != null && book.getPageCount() < 0) {
            throw new IllegalArgumentException("Количество страниц не может быть отрицательным");
        }

        // === ВАЛИДАЦИЯ ДЛИТЕЛЬНОСТИ ===
        if (book.getDurationMinutes() != null && book.getDurationMinutes() < 0) {
            throw new IllegalArgumentException("Длительность не может быть отрицательной");
        }

        // === СОХРАНЯЕМ СТАРЫЙ СТАТУС ДЛЯ СРАВНЕНИЯ (перед изменениями) ===
        BookStatus oldStatus = null;
        Integer oldPageCount = null;
        if (book.getId() != null) {
            Book existingBook = getBookById(book.getId());
            if (existingBook != null) {
                oldStatus = existingBook.getStatus();
                oldPageCount = existingBook.getPageCount();
            }
        }

        // === ВАЛИДАЦИЯ И НОРМАЛИЗАЦИЯ ISBN ===
        if (book.getIsbn() != null && !book.getIsbn().trim().isEmpty()) {
            String normalizedIsbn = IsbnUtils.normalize(book.getIsbn());
            book.setIsbn(normalizedIsbn);

            if (book.getId() != null) {
                boolean exists = bookRepository.existsByIsbnAndIdNot(normalizedIsbn, book.getId());
                if (exists) {
                    logger.warn("Книга с ISBN {} уже существует (при редактировании)", normalizedIsbn);
                    throw new IllegalArgumentException("Книга с таким ISBN уже существует");
                }
            } else {
                Optional<Book> existingBook = bookRepository.findByIsbn(normalizedIsbn);
                if (existingBook.isPresent()) {
                    logger.warn("Книга с ISBN {} уже существует", normalizedIsbn);
                    throw new IllegalArgumentException("Книга с таким ISBN уже существует");
                }
            }
        } else {
            book.setIsbn(null);
        }

        // === НОРМАЛИЗАЦИЯ ТЕКСТОВЫХ ПОЛЕЙ ===
        if (book.getTitle() != null && !book.getTitle().isEmpty()) {
            book.setTitle(normalizeText(book.getTitle()));
        }

        if (book.getAuthor() != null && !book.getAuthor().isEmpty()) {
            book.setAuthor(normalizeAuthor(book.getAuthor()));
        }

        if (book.getGenre() != null && !book.getGenre().isEmpty()) {
            book.setGenre(normalizeText(book.getGenre()));
        }

        if (book.getFormat() != null && !book.getFormat().isEmpty()) {
            book.setFormat(book.getFormat().trim().toUpperCase());
        }

        // === НОРМАЛИЗАЦИЯ ТЕГОВ ===
        if (book.getTags() != null) {
            Set<String> normalizedTags = book.getTags().stream()
                    .filter(tag -> tag != null && !tag.trim().isEmpty())
                    .map(String::trim)
                    .map(this::normalizeText)
                    .collect(Collectors.toSet());
            book.setTags(normalizedTags);
        }

        // === УСТАНОВКА ЗНАЧЕНИЙ ПО УМОЛЧАНИЮ ===
        if (book.getAddedDate() == null) {
            book.setAddedDate(LocalDateTime.now());
        }

        if (book.getStatus() == null) {
            book.setStatus(BookStatus.WANT_TO_READ);
        }

        if (book.getType() == null) {
            book.setType(BookType.PAPER);
        }

        // === ОПРЕДЕЛЯЕМ, БЫЛА ЛИ КНИГА ТОЛЬКО ЧТО ПРОЧИТАНА ===
        boolean wasJustCompleted = false;

        if (book.getId() != null) {
            if (oldStatus != null && oldStatus != BookStatus.READ && book.getStatus() == BookStatus.READ) {
                wasJustCompleted = true;
                logger.debug("Книга '{}' только что отмечена как прочитанная (была: {})",
                        book.getTitle(), oldStatus);
            }
        } else {
            if (book.getStatus() == BookStatus.READ) {
                wasJustCompleted = true;
                logger.debug("Новая книга '{}' добавлена как прочитанная", book.getTitle());
            }
        }

        // === СОХРАНЕНИЕ КНИГИ ===
        Book saved = bookRepository.save(book);
        logger.info("Книга успешно сохранена с ID: {}", saved.getId());

        // === АВТОМАТИЧЕСКОЕ ДОБАВЛЕНИЕ СТАТИСТИКИ ===
        if (wasJustCompleted) {
            try {
                int pagesRead = 0;
                if (book.getPageCount() != null && book.getPageCount() > 0) {
                    pagesRead = book.getPageCount();
                } else if (oldPageCount != null && oldPageCount > 0) {
                    pagesRead = oldPageCount;
                }

                LocalDate today = LocalDate.now();
                List<ReadingStats> todayStats = statsRepository.findByDateBetween(today, today);

                if (todayStats.isEmpty()) {
                    ReadingStats stats = new ReadingStats(today, pagesRead, 1);
                    statsRepository.save(stats);
                    logger.info("✅ Автоматически добавлена новая статистика чтения: +1 книга, +{} страниц", pagesRead);
                } else {
                    ReadingStats existingStats = todayStats.get(0);
                    existingStats.setBooksCompleted(existingStats.getBooksCompleted() + 1);
                    existingStats.setPagesRead(existingStats.getPagesRead() + pagesRead);
                    statsRepository.save(existingStats);
                    logger.info("✅ Обновлена статистика чтения за сегодня: +1 книга, +{} страниц", pagesRead);
                }
            } catch (Exception e) {
                logger.error("Не удалось автоматически добавить статистику для книги '{}': {}",
                        saved.getTitle(), e.getMessage());
            }
        }
    }

    /**
     * Удаляет книгу по её идентификатору.
     *
     * @param id идентификатор удаляемой книги
     */
    @Transactional
    public void deleteBook(Long id) {
        logger.info("Удаление книги с ID: {}", id);
        Book book = getBookById(id);
        if (book != null) {
            bookRepository.deleteById(id);
            logger.debug("Книга '{}' удалена", book.getTitle());
        } else {
            logger.warn("Попытка удалить несуществующую книгу с ID: {}", id);
        }
    }

    /**
     * Находит книгу по ISBN.
     *
     * @param isbn ISBN для поиска
     * @return объект книги или null, если книга не найдена
     */
    public Book getBookByIsbn(String isbn) {
        logger.debug("Поиск книги по ISBN: {}", isbn);
        if (isbn == null || isbn.isEmpty()) {
            return null;
        }
        return bookRepository.findByIsbn(isbn).orElse(null);
    }

    /**
     * Выполняет поиск книг по названию.
     *
     * @param title название книги
     * @return список найденных книг
     */
    public List<Book> searchByTitle(String title) {
        logger.debug("Поиск по названию: {}", title);
        if (title == null || title.isEmpty()) {
            return new ArrayList<>();
        }
        return bookRepository.findByTitleContainingIgnoreCase(title);
    }

    /**
     * Выполняет поиск книг по автору.
     *
     * @param author автор книги
     * @return список найденных книг
     */
    public List<Book> searchByAuthor(String author) {
        logger.debug("Поиск по автору: {}", author);
        if (author == null || author.isEmpty()) {
            return new ArrayList<>();
        }
        return bookRepository.findByAuthorContainingIgnoreCase(author);
    }

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
    public List<Book> advancedSearch(String title, String author, String genre,
                                     BookStatus status, Integer minRating, Integer maxRating) {
        logger.debug("Расширенный поиск: title={}, author={}, genre={}, status={}, minRating={}, maxRating={}",
                title, author, genre, status, minRating, maxRating);

        if (genre != null && !genre.isEmpty()) {
            genre = normalizeText(genre);
        }

        if (minRating != null && (minRating < 1 || minRating > 5)) {
            minRating = null;
        }
        if (maxRating != null && (maxRating < 1 || maxRating > 5)) {
            maxRating = null;
        }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            int temp = minRating;
            minRating = maxRating;
            maxRating = temp;
        }

        return bookRepository.advancedSearch(title, author, genre, status, minRating, maxRating);
    }

    /**
     * Находит книги по указанному тегу.
     *
     * @param tag тег для поиска
     * @return список книг с указанным тегом
     */
    public List<Book> getBooksByTag(String tag) {
        logger.debug("Поиск книг по тегу: {}", tag);
        if (tag == null || tag.isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedTag = normalizeText(tag);
        return bookRepository.findByTagsContaining(normalizedTag);
    }

    /**
     * Возвращает статистику распределения книг по жанрам.
     *
     * @return карта: жанр -> количество книг
     */
    public Map<String, Long> getGenreStatistics() {
        logger.debug("Получение статистики по жанрам");
        List<Object[]> results = bookRepository.getGenreStatisticsRaw();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (v1, v2) -> v1
                ));
    }

    /**
     * Возвращает статистику распределения книг по тегам.
     *
     * @return карта: тег -> количество книг
     */
    public Map<String, Long> getTagStatistics() {
        logger.debug("Получение статистики по тегам");
        List<Object[]> results = bookRepository.getTagStatisticsRaw();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (v1, v2) -> v1
                ));
    }

    /**
     * Возвращает список непрочитанных книг (статус WANT_TO_READ).
     *
     * @return список непрочитанных книг
     */
    public List<Book> getUnreadBooks() {
        logger.debug("Получение непрочитанных книг");
        return bookRepository.findByStatus(BookStatus.WANT_TO_READ);
    }

    /**
     * Добавляет запись статистики чтения за указанную дату.
     *
     * @param date           дата чтения
     * @param pagesRead      количество прочитанных страниц
     * @param booksCompleted количество завершенных книг
     * @throws IllegalArgumentException если значения страниц или книг отрицательные
     */
    @Transactional
    public void addReadingStats(LocalDate date, int pagesRead, int booksCompleted) {
        logger.info("Добавление статистики чтения: date={}, pagesRead={}, booksCompleted={}",
                date, pagesRead, booksCompleted);

        if (date == null) {
            date = LocalDate.now();
        }

        if (pagesRead < 0) {
            throw new IllegalArgumentException("Количество страниц не может быть отрицательным");
        }

        if (booksCompleted < 0) {
            throw new IllegalArgumentException("Количество книг не может быть отрицательным");
        }

        ReadingStats stats = new ReadingStats(date, pagesRead, booksCompleted);
        statsRepository.save(stats);
        logger.info("Статистика чтения успешно добавлена");
    }

    /**
     * Возвращает общее количество страниц, прочитанных в текущем месяце.
     *
     * @return количество страниц
     */
    public int getTotalPagesReadThisMonth() {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        Integer pages = statsRepository.sumPagesReadBetween(start, end);
        return pages != null ? pages : 0;
    }

    /**
     * Возвращает общее количество книг, завершенных в текущем месяце.
     *
     * @return количество книг
     */
    public int getBooksCompletedThisMonth() {
        YearMonth currentMonth = YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();
        Integer books = statsRepository.sumBooksCompletedBetween(start, end);
        return books != null ? books : 0;
    }

    /**
     * Добавляет тег к книге.
     *
     * @param bookId идентификатор книги
     * @param tag    тег для добавления
     * @throws IllegalArgumentException если тег пустой
     */
    @Transactional
    public void addTagToBook(Long bookId, String tag) {
        logger.debug("Добавление тега {} книге {}", tag, bookId);
        if (tag == null || tag.trim().isEmpty()) {
            throw new IllegalArgumentException("Тег не может быть пустым");
        }

        Book book = getBookById(bookId);
        if (book != null) {
            String normalizedTag = normalizeText(tag);
            book.getTags().add(normalizedTag);
            bookRepository.save(book);
            logger.debug("Тег {} успешно добавлен книге {}", normalizedTag, bookId);
        } else {
            logger.warn("Книга с ID {} не найдена для добавления тега", bookId);
        }
    }

    /**
     * Удаляет тег у книги.
     *
     * @param bookId идентификатор книги
     * @param tag    тег для удаления
     */
    @Transactional
    public void removeTagFromBook(Long bookId, String tag) {
        logger.debug("Удаление тега {} у книги {}", tag, bookId);
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }

        Book book = getBookById(bookId);
        if (book != null) {
            String normalizedTag = normalizeText(tag);
            book.getTags().remove(normalizedTag);
            bookRepository.save(book);
            logger.debug("Тег {} успешно удален у книги {}", normalizedTag, bookId);
        }
    }

    /**
     * Возвращает статистику чтения по месяцам за указанный год.
     *
     * @param year год для получения статистики
     * @return карта: номер месяца -> количество завершенных книг (Long)
     */
    public Map<Integer, Long> getMonthlyReadingStats(int year) {
        logger.debug("Получение статистики чтения за год: {}", year);
        List<ReadingStats> stats = statsRepository.findByDateBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );

        Map<Integer, Long> monthlyStats = new HashMap<>();
        for (int month = 1; month <= 12; month++) {
            final int currentMonth = month;
            long booksCompleted = stats.stream()
                    .filter(s -> s.getDate().getMonthValue() == currentMonth)
                    .mapToLong(ReadingStats::getBooksCompleted)
                    .sum();
            monthlyStats.put(month, booksCompleted);
        }
        return monthlyStats;
    }

    /**
     * Возвращает список доступных годов, за которые есть статистика.
     *
     * @return список годов (отсортированный по убыванию)
     */
    public List<Integer> getAvailableYears() {
        logger.debug("Получение доступных годов для статистики");
        List<ReadingStats> allStats = statsRepository.findAll();

        Set<Integer> years = new HashSet<>();
        for (ReadingStats stat : allStats) {
            if (stat.getDate() != null) {
                years.add(stat.getDate().getYear());
            }
        }

        // Добавляем текущий год, если данных нет
        if (years.isEmpty()) {
            years.add(LocalDate.now().getYear());
        }

        List<Integer> sortedYears = new ArrayList<>(years);
        sortedYears.sort(Comparator.reverseOrder());
        return sortedYears;
    }

    /**
     * Возвращает книги, находящиеся на указанной виртуальной полке (теге).
     *
     * @param shelfName название полки (тега)
     * @return список книг на полке
     */
    public List<Book> getBooksByVirtualShelf(String shelfName) {
        logger.debug("Получение книг с виртуальной полки: {}", shelfName);
        if (shelfName == null || shelfName.isEmpty()) {
            return new ArrayList<>();
        }
        return getBooksByTag(shelfName);
    }

    /**
     * Добавляет книгу на виртуальную полку (добавляет тег).
     *
     * @param bookId    идентификатор книги
     * @param shelfName название полки (тега)
     * @throws IllegalArgumentException если название полки пустое
     */
    @Transactional
    public void addToVirtualShelf(Long bookId, String shelfName) {
        logger.info("Добавление книги {} на полку {}", bookId, shelfName);
        if (shelfName == null || shelfName.trim().isEmpty()) {
            throw new IllegalArgumentException("Название полки не может быть пустым");
        }
        addTagToBook(bookId, shelfName);
    }

    /**
     * Возвращает сводную статистику для дашборда.
     *
     * @return карта со статистическими данными
     */
    public Map<String, Object> getDashboardStats() {
        logger.debug("Получение статистики для дашборда");
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBooks", bookRepository.count());
        stats.put("readBooks", bookRepository.countByStatus(BookStatus.READ));
        stats.put("wantToRead", bookRepository.countByStatus(BookStatus.WANT_TO_READ));
        stats.put("paperBooks", bookRepository.countByType(BookType.PAPER));
        stats.put("electronicBooks", bookRepository.countByType(BookType.ELECTRONIC));
        stats.put("audioBooks", bookRepository.countByType(BookType.AUDIO));

        Double avgRating = bookRepository.getAverageRating();
        stats.put("avgRating", avgRating != null ? avgRating : 0.0);

        stats.put("genreStats", getGenreStatistics());
        stats.put("pagesThisMonth", getTotalPagesReadThisMonth());
        stats.put("booksThisMonth", getBooksCompletedThisMonth());

        return stats;
    }

    /**
     * Нормализует текст для использования в качестве названия, жанра или тега.
     * <p>
     * Алгоритм нормализации:
     * <ol>
     *   <li>Удаляет начальные и конечные пробелы (trim)</li>
     *   <li>Приводит все символы к нижнему регистру</li>
     *   <li>Делает первую букву заглавной</li>
     * </ol>
     * </p>
     * <p>
     * Пример: " фАНТАСТИКА " → "Фантастика"
     * </p>
     *
     * @param text исходный текст
     * @return нормализованный текст или null, если входной текст null или пуст
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        text = text.trim().toLowerCase();
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    /**
     * Нормализует имя автора для обеспечения единообразия в базе данных.
     * <p>
     * Алгоритм нормализации:
     * <ol>
     *   <li>Разбивает имя на отдельные слова (по пробелам)</li>
     *   <li>Каждое слово приводит к формату "С заглавной буквы, остальные строчные"</li>
     *   <li>Собирает слова обратно через пробел</li>
     * </ol>
     * </p>
     * <p>
     * Важные особенности:
     * <ul>
     *   <li>Инициалы (например, "Дж. Р. Р. Толкин") обрабатываются корректно,
     *       так как каждая буква с точкой считается отдельным словом</li>
     *   <li>Фамилии с приставками (например, "де", "фон") также нормализуются</li>
     *   <li>Не делает предположений о структуре имени (первое/последнее слово)</li>
     * </ul>
     * </p>
     * <p>
     * Примеры:
     * <pre>
     * "лев толстой" → "Лев Толстой"
     * "Дж. р. р. толкин" → "Дж. Р. Р. Толкин"
     * "  ЭРИХ  МАРИЯ  РЕМАРК  " → "Эрих Мария Ремарк"
     * </pre>
     * </p>
     *
     * @param author имя автора
     * @return нормализованное имя автора или null, если входная строка null или пуста
     */
    private String normalizeAuthor(String author) {
        if (author == null || author.isEmpty()) {
            return author;
        }
        String[] words = author.trim().split("\\s+");
        StringBuilder normalized = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                normalized.append(word.substring(0, 1).toUpperCase())
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return normalized.toString().trim();
    }
}