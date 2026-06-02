package com.bookshelf.controller;

import com.bookshelf.model.*;
import com.bookshelf.service.BookService;
import com.bookshelf.service.GoogleBooksApiService;
import com.bookshelf.service.OpenLibraryApiService;
import com.bookshelf.service.RateLimitService;
import com.bookshelf.util.IsbnUtils;
import com.bookshelf.util.TagUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Контроллер для обработки HTTP-запросов, связанных с управлением книгами.
 * <p>
 * Обрабатывает запросы на отображение, добавление, редактирование, удаление,
 * поиск книг, а также работу с виртуальными полками, статистикой и отчетами.
 * </p>
 *
 * @author Bookshelf Team
 * @version 2.0
 */
@Controller
public class BookController {

    private static final Logger logger = LoggerFactory.getLogger(BookController.class);

    @Autowired
    private BookService bookService;

    @Autowired
    private GoogleBooksApiService googleBooksService;

    @Autowired
    private OpenLibraryApiService openLibraryService;

    @Autowired
    private RateLimitService rateLimitService;

    private final ObjectMapper objectMapper;

    /**
     * Конструктор контроллера, инициализирующий ObjectMapper для работы с JSON.
     */
    public BookController() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Отображает главную страницу (дашборд) с общей статистикой и недавно добавленными книгами.
     *
     * @param model для передачи атрибутов в представление
     * @return имя шаблона Thymeleaf "dashboard"
     */
    @GetMapping("/")
    public String home(Model model) {
        try {
            Map<String, Object> stats = bookService.getDashboardStats();
            model.addAttribute("stats", stats);
            List<Book> allBooks = bookService.getAllBooks();
            model.addAttribute("recentBooks", allBooks.stream().limit(10).toList());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке дашборда: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки данных");
            model.addAttribute("stats", Map.of());
            model.addAttribute("recentBooks", List.of());
        }
        return "dashboard";
    }

    /**
     * Отображает список всех книг с поддержкой пагинации, поиска и фильтрации.
     *
     * @param model     модель для передачи атрибутов
     * @param pageable  параметры пагинации и сортировки
     * @param search    поисковый запрос
     * @param status    фильтр по статусу книги
     * @param genre     фильтр по жанру
     * @param minRating минимальный рейтинг
     * @param maxRating максимальный рейтинг
     * @return имя шаблона "books"
     */
    @GetMapping("/books")
    public String listBooks(Model model,
                            @PageableDefault(size = 20, sort = "addedDate", direction = Sort.Direction.DESC) Pageable pageable,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) BookStatus status,
                            @RequestParam(required = false) String genre,
                            @RequestParam(required = false) Integer minRating,
                            @RequestParam(required = false) Integer maxRating) {
        try {
            Page<Book> bookPage;

            if (search != null && !search.isEmpty()) {
                bookPage = bookService.searchBooks(search, pageable);
            } else if (status != null || genre != null || minRating != null || maxRating != null) {
                bookPage = bookService.filterBooks(status, genre, minRating, maxRating, pageable);
            } else {
                bookPage = bookService.getAllBooks(pageable);
            }

            model.addAttribute("books", bookPage.getContent());
            model.addAttribute("currentPage", bookPage.getNumber());
            model.addAttribute("totalPages", bookPage.getTotalPages());
            model.addAttribute("totalItems", bookPage.getTotalElements());
            model.addAttribute("pageSize", pageable.getPageSize());
            model.addAttribute("statuses", BookStatus.values());
            model.addAttribute("types", BookType.values());
            model.addAttribute("currentSearch", search);

            String sortParam = pageable.getSort().isSorted() ?
                    pageable.getSort().iterator().next().getProperty() + "," +
                            pageable.getSort().iterator().next().getDirection().toString().toLowerCase() : null;
            model.addAttribute("currentSort", sortParam);

        } catch (Exception e) {
            logger.error("Ошибка при получении списка книг: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки списка книг");
            model.addAttribute("books", List.of());
        }
        return "books";
    }

    /**
     * Отображает форму фильтрации книг.
     *
     * @param model     модель для передачи атрибутов
     * @param status    текущий фильтр по статусу
     * @param genre     текущий фильтр по жанру
     * @param minRating текущий фильтр по минимальному рейтингу
     * @param maxRating текущий фильтр по максимальному рейтингу
     * @param size      размер страницы
     * @param sort      параметр сортировки
     * @param request   HTTP-запрос для получения referer URL
     * @return имя шаблона "filter-books"
     */
    @GetMapping("/books/filter")
    public String filterBooksForm(Model model,
                                  @RequestParam(required = false) BookStatus status,
                                  @RequestParam(required = false) String genre,
                                  @RequestParam(required = false) Integer minRating,
                                  @RequestParam(required = false) Integer maxRating,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String sort,
                                  HttpServletRequest request) {
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedGenre", genre);
        model.addAttribute("selectedMinRating", minRating);
        model.addAttribute("selectedMaxRating", maxRating);
        model.addAttribute("currentPageSize", size);
        model.addAttribute("currentSort", sort);

        List<String> genres = bookService.getAllGenres();
        model.addAttribute("genres", genres);

        String referer = request.getHeader("Referer");
        model.addAttribute("returnUrl", referer != null ? referer : "/books");

        return "filter-books";
    }

    /**
     * Отображает форму для добавления новой книги.
     *
     * @param model для передачи атрибутов
     * @return имя шаблона "add-book"
     */
    @GetMapping("/books/add")
    public String showAddForm(Model model) {
        model.addAttribute("book", new Book());
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("types", BookType.values());
        return "add-book";
    }

    /**
     * Обрабатывает отправку формы добавления новой книги.
     *
     * @param book      объект книги, привязанный к форме
     * @param tagsInput строка с тегами, разделенными запятыми
     * @param rating    рейтинг книги
     * @param model    для передачи атрибутов в случае ошибки
     * @return перенаправление на список книг или возврат к форме при ошибке
     */
    @PostMapping("/books/add")
    public String addBook(@ModelAttribute Book book,
                          @RequestParam(required = false) String tagsInput,
                          @RequestParam(required = false) Double rating,
                          Model model) {
        try {
            if (rating != null && rating > 0) {
                book.setRating(rating);
            } else {
                book.setRating(null);
            }

            if (tagsInput != null) {
                book.setTags(TagUtils.parseTags(tagsInput));
            }

            if (book.getAddedDate() == null) {
                book.setAddedDate(LocalDateTime.now());
            }

            bookService.saveBook(book);
            return "redirect:/books";
        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при добавлении книги: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("book", book);
            model.addAttribute("statuses", BookStatus.values());
            model.addAttribute("types", BookType.values());
            return "add-book";
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при добавлении книги: {}", e.getMessage());
            model.addAttribute("error", "Произошла ошибка при сохранении книги");
            model.addAttribute("book", book);
            model.addAttribute("statuses", BookStatus.values());
            model.addAttribute("types", BookType.values());
            return "add-book";
        }
    }

    /**
     * Обрабатывает отправку формы редактирования книги.
     *
     * @param id        идентификатор редактируемой книги
     * @param book      объект книги с обновленными данными
     * @param tagsInput строка с тегами, разделенными запятыми
     * @param rating    рейтинг книги
     * @param model    для передачи атрибутов в случае ошибки
     * @return перенаправление на список книг или возврат к форме при ошибке
     */
    @PostMapping("/books/edit/{id}")
    public String updateBook(@PathVariable Long id,
                             @ModelAttribute Book book,
                             @RequestParam(required = false) String tagsInput,
                             @RequestParam(required = false) Double rating,
                             Model model) {
        try {
            if (rating != null && rating > 0) {
                book.setRating(rating);
            } else {
                book.setRating(null);
            }

            if (tagsInput != null) {
                book.setTags(TagUtils.parseTags(tagsInput));
            } else {
                book.setTags(new HashSet<>());
            }

            book.setId(id);
            bookService.saveBook(book);
            return "redirect:/books";
        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при обновлении книги: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("book", book);
            model.addAttribute("statuses", BookStatus.values());
            model.addAttribute("types", BookType.values());
            return "edit-book";
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при обновлении книги: {}", e.getMessage());
            model.addAttribute("error", "Произошла ошибка при обновлении книги");
            model.addAttribute("book", book);
            model.addAttribute("statuses", BookStatus.values());
            model.addAttribute("types", BookType.values());
            return "edit-book";
        }
    }

    /**
     * Добавляет книгу в библиотеку из результатов поиска по внешнему API.
     *
     * @param title       название книги
     * @param author      автор книги
     * @param genre       жанр книги
     * @param isbn        ISBN книги
     * @param description описание книги
     * @param pageCount   количество страниц
     * @param rating      рейтинг книги
     * @param coverUrl    URL обложки книги
     * @return перенаправление на список книг
     */
    @PostMapping("/books/add-from-api")
    public String addBookFromApi(@RequestParam String title,
                                 @RequestParam String author,
                                 @RequestParam(required = false) String genre,
                                 @RequestParam(required = false) String isbn,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) Integer pageCount,
                                 @RequestParam(required = false) Double rating,
                                 @RequestParam(required = false) String coverUrl) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setGenre(genre);

        if (isbn != null && !isbn.trim().isEmpty()) {
            book.setIsbn(isbn);
        } else {
            book.setIsbn(null);
        }

        book.setDescription(description);
        book.setPageCount(pageCount);
        book.setStatus(BookStatus.WANT_TO_READ);
        book.setType(BookType.PAPER);
        book.setAddedDate(LocalDateTime.now());

        if (coverUrl != null && !coverUrl.trim().isEmpty()) {
            book.setCoverUrl(coverUrl);
        }

        if (rating != null && rating > 0) {
            book.setRating(rating);
        }

        bookService.saveBook(book);
        return "redirect:/books";
    }

    /**
     * Отображает форму редактирования книги.
     *
     * @param id     идентификатор редактируемой книги
     * @param model для передачи атрибутов
     * @return имя шаблона "edit-book" или перенаправление на список книг, если книга не найдена
     */
    @GetMapping("/books/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        try {
            Book book = bookService.getBookById(id);
            if (book == null) {
                return "redirect:/books";
            }
            model.addAttribute("book", book);
            model.addAttribute("statuses", BookStatus.values());
            model.addAttribute("types", BookType.values());
            return "edit-book";
        } catch (Exception e) {
            logger.error("Ошибка при загрузке формы редактирования: {}", e.getMessage());
            return "redirect:/books";
        }
    }

    /**
     * Удаляет книгу из библиотеки по её идентификатору.
     *
     * @param id идентификатор удаляемой книги
     * @return перенаправление на список книг
     */
    @GetMapping("/books/delete/{id}")
    public String deleteBook(@PathVariable Long id) {
        try {
            bookService.deleteBook(id);
        } catch (Exception e) {
            logger.error("Ошибка при удалении книги: {}", e.getMessage());
        }
        return "redirect:/books";
    }

    /**
     * Отображает страницу с подробной информацией о книге.
     *
     * @param id     идентификатор книги
     * @param model для передачи атрибутов
     * @return имя шаблона "view-book" или перенаправление на список книг, если книга не найдена
     */
    @GetMapping("/books/view/{id}")
    public String viewBook(@PathVariable Long id, Model model) {
        try {
            Book book = bookService.getBookById(id);
            if (book == null) {
                return "redirect:/books";
            }
            model.addAttribute("book", book);
            return "view-book";
        } catch (Exception e) {
            logger.error("Ошибка при просмотре книги: {}", e.getMessage());
            return "redirect:/books";
        }
    }

    /**
     * Отображает форму расширенного поиска книг.
     *
     * @param model для передачи атрибутов
     * @return имя шаблона "search"
     */
    @GetMapping("/search")
    public String searchForm(Model model) {
        model.addAttribute("statuses", BookStatus.values());
        return "search";
    }

    /**
     * Выполняет расширенный поиск книг по заданным критериям.
     *
     * @param title     название книги
     * @param author    автор книги
     * @param genre     жанр книги
     * @param status    статус книги
     * @param minRating минимальный рейтинг
     * @param maxRating максимальный рейтинг
     * @param model    для передачи атрибутов
     * @return имя шаблона "search-results"
     */
    @GetMapping("/search/advanced")
    public String advancedSearch(@RequestParam(required = false) String title,
                                 @RequestParam(required = false) String author,
                                 @RequestParam(required = false) String genre,
                                 @RequestParam(required = false) BookStatus status,
                                 @RequestParam(required = false) Integer minRating,
                                 @RequestParam(required = false) Integer maxRating,
                                 Model model) {
        try {
            List<Book> results = bookService.advancedSearch(title, author, genre, status, minRating, maxRating);
            model.addAttribute("results", results);
        } catch (Exception e) {
            logger.error("Ошибка при поиске: {}", e.getMessage());
            model.addAttribute("results", List.of());
            model.addAttribute("error", "Ошибка при выполнении поиска");
        }
        model.addAttribute("statuses", BookStatus.values());
        return "search-results";
    }

    /**
     * Отображает форму для сканирования ISBN.
     *
     * @return имя шаблона "scan-isbn"
     */
    @GetMapping("/scan-isbn")
    public String scanIsbnForm() {
        return "scan-isbn";
    }

    /**
     * Обрабатывает поиск книги по ISBN с использованием выбранного API.
     *
     * @param isbn      ISBN для поиска
     * @param apiSource источник API (google или openlibrary), по умолчанию openlibrary
     * @param model     модель для передачи атрибутов
     * @param request   HTTP-запрос для проверки ограничения частоты запросов
     * @return имя шаблона "add-book" при успешном нахождении книги, иначе "scan-isbn"
     */
    @PostMapping("/scan-isbn")
    public String scanIsbn(@RequestParam String isbn,
                           @RequestParam(required = false, defaultValue = "openlibrary") String apiSource,
                           Model model,
                           HttpServletRequest request) {

        if (rateLimitService.isRateLimited(request)) {
            model.addAttribute("error", "Слишком частые запросы. Подождите несколько секунд.");
            return "scan-isbn";
        }

        try {
            if (!IsbnUtils.isValid(isbn)) {
                model.addAttribute("error", "Некорректный формат ISBN. Введите 10 или 13 цифр.");
                return "scan-isbn";
            }

            String normalizedIsbn = IsbnUtils.normalize(isbn);
            Book book = null;
            String apiUsed = "Open Library";

            if ("google".equalsIgnoreCase(apiSource)) {
                logger.info("Поиск по ISBN через Google Books API: {}", normalizedIsbn);
                try {
                    book = googleBooksService.searchByIsbn(normalizedIsbn);
                    apiUsed = "Google Books";
                } catch (RuntimeException e) {
                    logger.error("Ошибка Google Books API: {}", e.getMessage());
                    model.addAttribute("error", "Ошибка Google Books API: " + e.getMessage());
                    return "scan-isbn";
                }
            } else {
                logger.info("Поиск по ISBN через Open Library API: {}", normalizedIsbn);
                try {
                    book = openLibraryService.searchByIsbn(normalizedIsbn);
                    apiUsed = "Open Library";
                } catch (RuntimeException e) {
                    logger.error("Ошибка Open Library API: {}", e.getMessage());
                    model.addAttribute("error", "Ошибка Open Library API: " + e.getMessage());
                    return "scan-isbn";
                }
            }

            if (book != null) {
                if (book.getIsbn() == null || book.getIsbn().isEmpty()) {
                    book.setIsbn(normalizedIsbn);
                }

                model.addAttribute("book", book);
                model.addAttribute("statuses", BookStatus.values());
                model.addAttribute("types", BookType.values());
                model.addAttribute("apiUsed", apiUsed);
                model.addAttribute("success", "Книга найдена в " + apiUsed + "! Проверьте данные и сохраните.");
                return "add-book";
            }

            model.addAttribute("error", "Книга с ISBN " + normalizedIsbn + " не найдена в " + apiUsed);
            return "scan-isbn";

        } catch (IllegalArgumentException e) {
            logger.error("Ошибка валидации ISBN: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "scan-isbn";
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при поиске по ISBN: {}", e.getMessage(), e);
            model.addAttribute("error", "Произошла ошибка при поиске книги. Пожалуйста, попробуйте позже.");
            return "scan-isbn";
        }
    }

    /**
     * Отображает форму для поиска книг в Google Books API.
     *
     * @return имя шаблона "search-google"
     */
    @GetMapping("/search-google")
    public String searchGoogleForm() {
        return "search-google";
    }

    /**
     * Выполняет поиск книг в Google Books API по заданному запросу.
     *
     * @param query   поисковый запрос
     * @param model   модель для передачи атрибутов
     * @param request HTTP-запрос для проверки ограничения частоты запросов
     * @return имя шаблона "api-results"
     */
    @PostMapping("/search-google")
    public String searchGoogle(@RequestParam String query, Model model, HttpServletRequest request) {
        if (rateLimitService.isRateLimited(request)) {
            model.addAttribute("error", "Слишком частые запросы. Подождите несколько секунд.");
            model.addAttribute("results", new ArrayList<>());
            model.addAttribute("api", "Google Books");
            return "api-results";
        }

        try {
            List<Book> results = googleBooksService.searchBooks(query);
            model.addAttribute("results", results);
            model.addAttribute("api", "Google Books");
            return "api-results";
        } catch (Exception e) {
            logger.error("Ошибка при поиске в Google Books: {}", e.getMessage());
            model.addAttribute("error", "Сервис Google Books временно недоступен. Пожалуйста, попробуйте позже.");
            model.addAttribute("results", new ArrayList<>());
            model.addAttribute("api", "Google Books");
            return "api-results";
        }
    }

    /**
     * Отображает форму для поиска книг в Open Library API.
     *
     * @return имя шаблона "search-openlibrary"
     */
    @GetMapping("/search-openlibrary")
    public String searchOpenLibraryForm() {
        return "search-openlibrary";
    }

    /**
     * Выполняет поиск книг в Open Library API по заданному запросу.
     *
     * @param query   поисковый запрос
     * @param model   модель для передачи атрибутов
     * @param request HTTP-запрос для проверки ограничения частоты запросов
     * @return имя шаблона "api-results"
     */
    @PostMapping("/search-openlibrary")
    public String searchOpenLibrary(@RequestParam String query, Model model, HttpServletRequest request) {
        if (rateLimitService.isRateLimited(request)) {
            model.addAttribute("error", "Слишком частые запросы. Подождите несколько секунд.");
            model.addAttribute("results", new ArrayList<>());
            model.addAttribute("api", "Open Library");
            return "api-results";
        }

        try {
            List<Book> results = openLibraryService.searchBooks(query);
            model.addAttribute("results", results);
            model.addAttribute("api", "Open Library");
            return "api-results";
        } catch (Exception e) {
            logger.error("Ошибка при поиске в Open Library: {}", e.getMessage());
            model.addAttribute("error", "Сервис Open Library временно недоступен. Пожалуйста, попробуйте позже.");
            model.addAttribute("results", new ArrayList<>());
            model.addAttribute("api", "Open Library");
            return "api-results";
        }
    }

    /**
     * Возвращает статистику по жанрам в формате JSON для круговой диаграммы.
     *
     * @return карта с данными для круговой диаграммы
     */
    @GetMapping("/stats/genre-data")
    @ResponseBody
    public Map<String, Object> getGenreStatsData() {
        try {
            Map<String, Long> genreStats = bookService.getGenreStatistics();

            List<Map.Entry<String, Long>> sorted = genreStats.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .toList();

            List<String> labels = new ArrayList<>();
            List<Long> values = new ArrayList<>();

            long otherSum = 0;
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<String, Long> entry = sorted.get(i);
                if (i < 5) {
                    labels.add(entry.getKey());
                    values.add(entry.getValue());
                } else {
                    otherSum += entry.getValue();
                }
            }

            if (otherSum > 0) {
                labels.add("Другие");
                values.add(otherSum);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("labels", labels);
            result.put("values", values);
            result.put("total", genreStats.values().stream().mapToLong(Long::longValue).sum());

            return result;
        } catch (Exception e) {
            logger.error("Ошибка при получении данных для диаграммы жанров: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Отображает страницу со статистикой чтения.
     *
     * @param model модель для передачи атрибутов
     * @return имя шаблона "statistics"
     */
    @GetMapping("/stats")
    public String statistics(Model model) {
        try {
            model.addAttribute("genreStats", bookService.getGenreStatistics());
            model.addAttribute("tagStats", bookService.getTagStatistics());
            model.addAttribute("monthlyStats", bookService.getMonthlyReadingStats(LocalDate.now().getYear()));
            model.addAttribute("unreadBooks", bookService.getUnreadBooks());
            model.addAttribute("pagesThisMonth", bookService.getTotalPagesReadThisMonth());
            model.addAttribute("booksThisMonth", bookService.getBooksCompletedThisMonth());
            model.addAttribute("availableYears", bookService.getAvailableYears());
            model.addAttribute("currentYear", LocalDate.now().getYear());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке статистики: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки статистики");
            model.addAttribute("availableYears", List.of(LocalDate.now().getYear()));
            model.addAttribute("currentYear", LocalDate.now().getYear());
        }
        return "statistics";
    }

    /**
     * Добавляет запись о прочитанных страницах и завершенных книгах за определенную дату.
     *
     * @param date           дата чтения
     * @param pagesRead      количество прочитанных страниц
     * @param booksCompleted количество завершенных книг
     * @param model          модель для передачи атрибутов в случае ошибки
     * @return перенаправление на страницу статистики
     */
    @PostMapping("/stats/add")
    public String addReadingStats(@RequestParam LocalDate date,
                                  @RequestParam int pagesRead,
                                  @RequestParam int booksCompleted,
                                  Model model) {
        try {
            if (date == null) {
                date = LocalDate.now();
            }
            bookService.addReadingStats(date, pagesRead, booksCompleted);
        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при добавлении статистики: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при добавлении статистики: {}", e.getMessage());
            model.addAttribute("error", "Ошибка при сохранении статистики");
        }
        return "redirect:/stats";
    }

    /**
     * Отображает список виртуальных полок (тегов).
     *
     * @param model модель для передачи атрибутов
     * @return имя шаблона "shelves"
     */
    @GetMapping("/shelves")
    public String virtualShelves(Model model) {
        try {
            Map<String, Long> tagStats = bookService.getTagStatistics();
            model.addAttribute("shelves", tagStats.keySet());
            model.addAttribute("shelfCounts", tagStats);
        } catch (Exception e) {
            logger.error("Ошибка при загрузке полок: {}", e.getMessage());
            model.addAttribute("shelves", List.of());
            model.addAttribute("shelfCounts", Map.of());
        }
        return "shelves";
    }

    /**
     * Отображает книги, находящиеся на указанной виртуальной полке (теге).
     *
     * @param shelfName название полки (тега)
     * @param model     модель для передачи атрибутов
     * @return имя шаблона "shelf-books"
     */
    @GetMapping("/shelves/{shelfName}")
    public String viewShelf(@PathVariable String shelfName, Model model) {
        try {
            model.addAttribute("shelfName", shelfName);
            model.addAttribute("books", bookService.getBooksByVirtualShelf(shelfName));
        } catch (Exception e) {
            logger.error("Ошибка при просмотре полки: {}", e.getMessage());
            model.addAttribute("books", List.of());
        }
        return "shelf-books";
    }

    /**
     * Подготавливает опции для выпадающего списка статусов.
     *
     * @param book книга для определения выбранного статуса
     * @return список опций статусов
     */
    @ModelAttribute("statusSelectOptions")
    public List<SelectOption> getStatusSelectOptions(@ModelAttribute("book") Book book) {
        List<SelectOption> options = new ArrayList<>();

        options.add(new SelectOption("WANT_TO_READ", "В планах", "/icons/status/icon-status-planned.png"));
        options.add(new SelectOption("IN_PROGRESS", "В процессе", "/icons/status/icon-status-progress.png"));
        options.add(new SelectOption("READ", "Прочитано", "/icons/status/icon-status-read.png"));

        return options;
    }

    /**
     * Подготавливает опции для выпадающего списка типов книг.
     *
     * @param book книга для определения выбранного типа
     * @return список опций типов книг
     */
    @ModelAttribute("typeSelectOptions")
    public List<SelectOption> getTypeSelectOptions(@ModelAttribute("book") Book book) {
        List<SelectOption> options = new ArrayList<>();

        options.add(new SelectOption("PAPER", "Бумажная книга", "/icons/type/icon-type-paper.png"));
        options.add(new SelectOption("ELECTRONIC", "Электронная книга", "/icons/type/icon-type-electronic.png"));
        options.add(new SelectOption("AUDIO", "Аудиокнига", "/icons/type/icon-type-audio.png"));

        return options;
    }

    /**
     * Возвращает статистику чтения по месяцам для указанного года в формате JSON.
     *
     * @param year год для получения статистики
     * @return карта с данными для графика
     */
    @GetMapping("/stats/monthly-data")
    @ResponseBody
    public Map<String, Object> getMonthlyStatsData(@RequestParam int year) {
        try {
            Map<Integer, Long> monthlyStats = bookService.getMonthlyReadingStats(year);
            List<Integer> months = new ArrayList<>();
            List<Long> values = new ArrayList<>();

            for (int i = 1; i <= 12; i++) {
                months.add(i);
                values.add(monthlyStats.getOrDefault(i, 0L));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("months", months);
            result.put("values", values);
            result.put("year", year);

            List<Integer> availableYears = bookService.getAvailableYears();
            result.put("availableYears", availableYears);

            return result;
        } catch (Exception e) {
            logger.error("Ошибка при получении данных для графика: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Добавляет книгу на виртуальную полку (добавляет тег).
     *
     * @param bookId    идентификатор книги
     * @param shelfName название полки (тега)
     * @param model     модель для передачи атрибутов в случае ошибки
     * @return перенаправление на страницу просмотра книги
     */
    @PostMapping("/shelves/add")
    public String addToShelf(@RequestParam Long bookId,
                             @RequestParam String shelfName,
                             Model model) {
        try {
            bookService.addToVirtualShelf(bookId, shelfName);
        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при добавлении на полку: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при добавлении на полку: {}", e.getMessage());
            model.addAttribute("error", "Ошибка при добавлении книги на полку");
        }
        return "redirect:/books/view/" + bookId;
    }

    /**
     * Отображает отчет о непрочитанных книгах.
     *
     * @param model модель для передачи атрибутов
     * @return имя шаблона "unread-report"
     */
    @GetMapping("/reports/unread")
    public String unreadReport(Model model) {
        try {
            model.addAttribute("unreadBooks", bookService.getUnreadBooks());
            model.addAttribute("totalUnread", bookService.getUnreadBooks().size());
            model.addAttribute("stats", bookService.getDashboardStats());
        } catch (Exception e) {
            logger.error("Ошибка при формировании отчета: {}", e.getMessage());
            model.addAttribute("error", "Ошибка формирования отчета");
            model.addAttribute("unreadBooks", List.of());
            model.addAttribute("totalUnread", 0);
            Map<String, Object> emptyStats = new HashMap<>();
            emptyStats.put("totalBooks", 0);
            emptyStats.put("readBooks", 0);
            emptyStats.put("wantToRead", 0);
            emptyStats.put("avgRating", 0.0);
            model.addAttribute("stats", emptyStats);
        }
        return "unread-report";
    }

    /**
     * Отображает книги, имеющие указанный тег.
     *
     * @param tag   тег для поиска книг
     * @param model модель для передачи атрибутов
     * @return имя шаблона "tag-books"
     */
    @GetMapping("/books/tag/{tag}")
    public String booksByTag(@PathVariable String tag, Model model) {
        try {
            model.addAttribute("tag", tag);
            model.addAttribute("books", bookService.getBooksByTag(tag));
        } catch (Exception e) {
            logger.error("Ошибка при поиске книг по тегу: {}", e.getMessage());
            model.addAttribute("books", List.of());
        }
        return "tag-books";
    }

    /**
     * Возвращает метку текущей сортировки для отображения в пользовательском интерфейсе.
     *
     * @param sort параметр сортировки
     * @return текстовое описание текущей сортировки
     */
    @ModelAttribute("currentSortLabel")
    public String getCurrentSortLabel(@RequestParam(required = false) String sort) {
        if (sort == null || sort.isEmpty()) {
            return "По дате (новые)";
        }

        return switch (sort) {
            case "title,asc" -> "По названию (А→Я)";
            case "title,desc" -> "По названию (Я→А)";
            case "author,asc" -> "По автору (А→Я)";
            case "author,desc" -> "По автору (Я→А)";
            case "addedDate,asc" -> "По дате (старые)";
            case "addedDate,desc" -> "По дате (новые)";
            case "rating,asc" -> "По рейтингу (низкий)";
            case "rating,desc" -> "По рейтингу (высокий)";
            default -> "Сортировка";
        };
    }

    /**
     * Возвращает список доступных опций сортировки.
     *
     * @return список объектов {@link SortOption} с описанием сортировки
     */
    @ModelAttribute("sortOptions")
    public List<SortOption> getSortOptions() {
        List<SortOption> options = new ArrayList<>();
        options.add(new SortOption("title,asc", "По названию (А→Я)", "icon-sort-alpha.png"));
        options.add(new SortOption("title,desc", "По названию (Я→А)", "icon-sort-beta.png"));
        options.add(null);
        options.add(new SortOption("author,asc", "По автору (А→Я)", "icon-sort-alpha.png"));
        options.add(new SortOption("author,desc", "По автору (Я→А)", "icon-sort-beta.png"));
        options.add(null);
        options.add(new SortOption("addedDate,desc", "По дате (новые сначала)", "icon-sort-date.png"));
        options.add(new SortOption("addedDate,asc", "По дате (старые сначала)", "icon-sort-date.png"));
        options.add(null);
        options.add(new SortOption("rating,desc", "По рейтингу (сначала высокий)", "icon-sort-rating.png"));
        options.add(new SortOption("rating,asc", "По рейтингу (сначала низкий)", "icon-sort-rating.png"));
        return options;
    }

    // ==================== НОВЫЕ МЕТОДЫ ДЛЯ ЭКСПОРТА/ИМПОРТА ====================

    /**
     * Экспортирует все книги из библиотеки в JSON файл.
     * <p>
     * Метод собирает все книги из базы данных, преобразует их в JSON формат
     * и отправляет клиенту в виде файла для скачивания. Имя файла генерируется
     * автоматически с текущей датой и временем.
     * </p>
     *
     * @param response HTTP-ответ для записи файла
     */
    @GetMapping("/export/books")
    public void exportBooks(HttpServletResponse response) {
        logger.info("Начало экспорта всех книг");

        try {
            List<Book> books = bookService.getAllBooks();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "bookshelf_export_" + timestamp + ".json";

            response.setContentType("application/json");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> exportData = new LinkedHashMap<>();
            exportData.put("exportDate", LocalDateTime.now().toString());
            exportData.put("totalBooks", books.size());
            exportData.put("books", books);

            objectMapper.writeValue(response.getWriter(), exportData);

            logger.info("Экспорт завершён. Экспортировано {} книг", books.size());

        } catch (Exception e) {
            logger.error("Ошибка при экспорте книг: {}", e.getMessage(), e);
            try {
                response.setContentType("text/html");
                response.getWriter().write("<html><body><h1>Ошибка экспорта</h1><p>" + e.getMessage() + "</p></body></html>");
            } catch (Exception ex) {
                logger.error("Не удалось записать ошибку в ответ: {}", ex.getMessage());
            }
        }
    }

    /**
     * Экспортирует все книги в CSV файл.
     */
    @GetMapping("/export/books/csv")
    public void exportBooksCsv(HttpServletResponse response) {
        try {
            List<Book> books = bookService.getAllBooks();
            String filename = "bookshelf_export_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setCharacterEncoding("UTF-8");

            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8),
                    ';', CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END)) {

                String[] headers = {"ID", "Название", "Автор", "Жанр", "ISBN", "Тип", "Статус", "Рейтинг", "Страниц"};
                writer.writeNext(headers);

                for (Book book : books) {
                    String[] row = {
                            book.getId() != null ? book.getId().toString() : "",
                            book.getTitle() != null ? book.getTitle() : "",
                            book.getAuthor() != null ? book.getAuthor() : "",
                            book.getGenre() != null ? book.getGenre() : "",
                            book.getIsbn() != null ? book.getIsbn() : "",
                            book.getType() != null ? book.getType().getDisplayName() : "",
                            book.getStatus() != null ? book.getStatus().getDisplayName() : "",
                            book.getRating() != null ? book.getRating().toString() : "",
                            book.getPageCount() != null ? book.getPageCount().toString() : ""
                    };
                    writer.writeNext(row);
                }
            }
            logger.info("CSV экспорт завершён. Экспортировано {} книг", books.size());
        } catch (Exception e) {
            logger.error("Ошибка при экспорте в CSV: {}", e.getMessage());
        }
    }

    /**
     * Экспортирует все книги в Excel файл.
     */
    @GetMapping("/export/books/excel")
    public void exportBooksExcel(HttpServletResponse response) {
        try {
            List<Book> books = bookService.getAllBooks();
            String filename = "bookshelf_export_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Books");
                Row headerRow = sheet.createRow(0);
                String[] headers = {"ID", "Название", "Автор", "Жанр", "ISBN", "Тип", "Статус", "Рейтинг", "Страниц"};
                for (int i = 0; i < headers.length; i++) {
                    headerRow.createCell(i).setCellValue(headers[i]);
                }

                int rowNum = 1;
                for (Book book : books) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(book.getId() != null ? book.getId() : 0);
                    row.createCell(1).setCellValue(book.getTitle() != null ? book.getTitle() : "");
                    row.createCell(2).setCellValue(book.getAuthor() != null ? book.getAuthor() : "");
                    row.createCell(3).setCellValue(book.getGenre() != null ? book.getGenre() : "");
                    row.createCell(4).setCellValue(book.getIsbn() != null ? book.getIsbn() : "");
                    row.createCell(5).setCellValue(book.getType() != null ? book.getType().getDisplayName() : "");
                    row.createCell(6).setCellValue(book.getStatus() != null ? book.getStatus().getDisplayName() : "");
                    row.createCell(7).setCellValue(book.getRating() != null ? book.getRating() : 0);
                    row.createCell(8).setCellValue(book.getPageCount() != null ? book.getPageCount() : 0);
                }
                workbook.write(response.getOutputStream());
            }
            logger.info("Excel экспорт завершён. Экспортировано {} книг", books.size());
        } catch (Exception e) {
            logger.error("Ошибка при экспорте в Excel: {}", e.getMessage());
        }
    }

    // ==================== МЕТОДЫ ИМПОРТА ====================

    @PostMapping("/import/books")
    public String importBooks(@RequestParam("file") MultipartFile file, Model model) {
        logger.info("Начало импорта книг из файла: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            model.addAttribute("error", "Пожалуйста, выберите файл для импорта");
            addUnreadReportAttributes(model);
            return "unread-report";
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            model.addAttribute("error", "Неверное имя файла");
            addUnreadReportAttributes(model);
            return "unread-report";
        }

        try {
            if (filename.endsWith(".json")) {
                importFromJson(file, model);
            } else if (filename.endsWith(".csv")) {
                importFromCsv(file, model);
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                importFromExcel(file, model);
            } else {
                model.addAttribute("error", "Поддерживаются форматы: JSON, CSV, Excel");
            }
        } catch (Exception e) {
            logger.error("Ошибка при импорте: {}", e.getMessage(), e);
            model.addAttribute("error", "Ошибка при импорте: " + e.getMessage());
        }

        addUnreadReportAttributes(model);

        return "unread-report";
    }

    /**
     * Добавляет в модель все необходимые атрибуты для страницы отчёта о непрочитанных книгах
     */
    private void addUnreadReportAttributes(Model model) {
        try {
            List<Book> unreadBooks = bookService.getUnreadBooks();
            model.addAttribute("unreadBooks", unreadBooks);
            model.addAttribute("totalUnread", unreadBooks.size());
            model.addAttribute("stats", bookService.getDashboardStats());
        } catch (Exception e) {
            logger.error("Ошибка при загрузке данных для отчёта: {}", e.getMessage());
            model.addAttribute("error", "Ошибка загрузки данных");
            model.addAttribute("unreadBooks", List.of());
            model.addAttribute("totalUnread", 0);
            Map<String, Object> emptyStats = new HashMap<>();
            emptyStats.put("totalBooks", 0);
            emptyStats.put("readBooks", 0);
            emptyStats.put("wantToRead", 0);
            emptyStats.put("avgRating", 0.0);
            model.addAttribute("stats", emptyStats);
        }
    }

    /**
     * Импортирует книги из JSON файла.
     *
     * @param file загруженный JSON файл
     * @param model модель для передачи атрибутов
     * @throws Exception если произошла ошибка при парсинге
     */
    private void importFromJson(MultipartFile file, Model model) throws Exception {
        logger.info("Импорт из JSON файла");

        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> importData = objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});

            Object booksObj = importData.get("books");
            if (!(booksObj instanceof List<?> booksRaw)) {
                model.addAttribute("error", "Неверный формат JSON: поле 'books' должно быть массивом");
                return;
            }

            if (booksRaw.isEmpty()) {
                model.addAttribute("error", "Файл не содержит книг для импорта");
                return;
            }

            List<Book> successfullyImported = new ArrayList<>();
            List<String> skippedBooks = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (Object item : booksRaw) {
                if (!(item instanceof Map<?, ?> bookData)) {
                    errors.add("Пропущен элемент неверного формата (не объект)");
                    continue;
                }

                try {
                    Book book = parseBookFromJsonMap(bookData);

                    if (book.getTitle() == null || book.getTitle().trim().isEmpty() ||
                            book.getAuthor() == null || book.getAuthor().trim().isEmpty()) {
                        skippedBooks.add("Книга без названия или автора: " +
                                (book.getTitle() != null ? book.getTitle() : "без названия"));
                        continue;
                    }

                    if (isBookExists(book, skippedBooks)) {
                        continue;
                    }

                    bookService.saveBook(book);
                    successfullyImported.add(book);

                } catch (Exception e) {
                    logger.error("Ошибка при импорте книги из JSON: {}", e.getMessage());
                    errors.add("Ошибка при импорте: " + e.getMessage());
                }
            }

            setImportResult(model, successfullyImported, skippedBooks, errors);
        }
    }

    /**
     * Импортирует книги из CSV файла.
     * <p>
     * Ожидаемый формат CSV (разделитель точка с запятой):
     * ID;Название;Автор;Жанр;ISBN;Тип;Статус;Рейтинг;Страниц
     * </p>
     *
     * @param file загруженный CSV файл
     * @param model модель для передачи атрибутов
     * @throws Exception если произошла ошибка при чтении файла
     */
    private void importFromCsv(MultipartFile file, Model model) throws Exception {
        logger.info("Импорт из CSV файла");

        List<Book> successfullyImported = new ArrayList<>();
        List<String> skippedBooks = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
        )) {

            String[] row;
            int rowNumber = 1;

            while ((row = reader.readNext()) != null) {
                rowNumber++;

                try {
                    if (row.length < 9) {
                        errors.add("Строка " + rowNumber + ": недостаточно колонок (ожидается 9, получено " + row.length + ")");
                        continue;
                    }

                    Book book = parseBookFromCsvRow(row);

                    if (book.getTitle() == null || book.getTitle().trim().isEmpty() ||
                            book.getAuthor() == null || book.getAuthor().trim().isEmpty()) {
                        skippedBooks.add("Строка " + rowNumber + ": книга без названия или автора");
                        continue;
                    }

                    if (isBookExists(book, skippedBooks)) {
                        continue;
                    }

                    bookService.saveBook(book);
                    successfullyImported.add(book);

                } catch (Exception e) {
                    logger.error("Ошибка при импорте строки CSV {}: {}", rowNumber, e.getMessage());
                    errors.add("Ошибка в строке " + rowNumber + ": " + e.getMessage());
                }
            }
        }

        setImportResult(model, successfullyImported, skippedBooks, errors);
    }

    /**
     * Импортирует книги из Excel файла.
     * <p>
     * Ожидаемый формат Excel (первая строка - заголовок):
     * ID | Название | Автор | Жанр | ISBN | Тип | Статус | Рейтинг | Страниц
     * </p>
     *
     * @param file загруженный Excel файл
     * @param model модель для передачи атрибутов
     * @throws Exception если произошла ошибка при чтении файла
     */
    private void importFromExcel(MultipartFile file, Model model) throws Exception {
        logger.info("Импорт из Excel файла");

        List<Book> successfullyImported = new ArrayList<>();
        List<String> skippedBooks = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                try {
                    Book book = parseBookFromExcelRow(row);

                    if (book.getTitle() == null || book.getTitle().trim().isEmpty() ||
                            book.getAuthor() == null || book.getAuthor().trim().isEmpty()) {
                        skippedBooks.add("Строка " + (rowNum + 1) + ": книга без названия или автора");
                        continue;
                    }

                    if (isBookExists(book, skippedBooks)) {
                        continue;
                    }

                    bookService.saveBook(book);
                    successfullyImported.add(book);

                } catch (Exception e) {
                    logger.error("Ошибка при импорте строки Excel {}: {}", rowNum + 1, e.getMessage());
                    errors.add("Ошибка в строке " + (rowNum + 1) + ": " + e.getMessage());
                }
            }
        }

        setImportResult(model, successfullyImported, skippedBooks, errors);
    }

    /**
     * Парсит книгу из JSON карты.
     *
     * @param bookData карта с данными книги
     * @return объект Book
     */
    private Book parseBookFromJsonMap(Map<?, ?> bookData) {
        Book book = new Book();

        // Строковые поля
        book.setTitle(getStringFromMap(bookData, "title"));
        book.setAuthor(getStringFromMap(bookData, "author"));
        book.setGenre(getStringFromMap(bookData, "genre"));
        book.setIsbn(getStringFromMap(bookData, "isbn"));
        book.setDescription(getStringFromMap(bookData, "description"));
        book.setReview(getStringFromMap(bookData, "review"));
        book.setCoverUrl(getStringFromMap(bookData, "coverUrl"));
        book.setFormat(getStringFromMap(bookData, "format"));

        // Числовые поля
        Object pageCountObj = bookData.get("pageCount");
        if (pageCountObj instanceof Number) {
            book.setPageCount(((Number) pageCountObj).intValue());
        }

        Object durationMinutesObj = bookData.get("durationMinutes");
        if (durationMinutesObj instanceof Number) {
            book.setDurationMinutes(((Number) durationMinutesObj).intValue());
        }

        Object ratingObj = bookData.get("rating");
        if (ratingObj instanceof Number) {
            double rating = ((Number) ratingObj).doubleValue();
            if (rating >= 0.5 && rating <= 5) {
                book.setRating(rating);
            }
        }

        // Enum поля
        Object statusObj = bookData.get("status");
        if (statusObj instanceof String) {
            try {
                book.setStatus(BookStatus.valueOf((String) statusObj));
            } catch (IllegalArgumentException e) {
                book.setStatus(BookStatus.WANT_TO_READ);
            }
        }

        Object typeObj = bookData.get("type");
        if (typeObj instanceof String) {
            try {
                book.setType(BookType.valueOf((String) typeObj));
            } catch (IllegalArgumentException e) {
                book.setType(BookType.PAPER);
            }
        }

        // Теги
        Object tagsObj = bookData.get("tags");
        if (tagsObj instanceof List<?> tagsListRaw) {
            Set<String> tagsSet = new HashSet<>();
            for (Object tagObj : tagsListRaw) {
                if (tagObj instanceof String) {
                    tagsSet.add((String) tagObj);
                }
            }
            book.setTags(tagsSet);
        }

        // Дата добавления
        Object addedDateObj = bookData.get("addedDate");
        if (addedDateObj instanceof String) {
            try {
                String dateStr = (String) addedDateObj;
                book.setAddedDate(LocalDateTime.parse(dateStr));
            } catch (Exception e) {
                book.setAddedDate(LocalDateTime.now());
            }
        } else {
            book.setAddedDate(LocalDateTime.now());
        }

        // Значения по умолчанию
        if (book.getStatus() == null) {
            book.setStatus(BookStatus.WANT_TO_READ);
        }
        if (book.getType() == null) {
            book.setType(BookType.PAPER);
        }

        return book;
    }

    /**
     * Парсит книгу из строки CSV.
     * <p>
     * Ожидаемый порядок колонок:
     * 0 - ID (игнорируется), 1 - Название, 2 - Автор, 3 - Жанр, 4 - ISBN,
     * 5 - Тип, 6 - Статус, 7 - Рейтинг, 8 - Страниц
     * </p>
     *
     * @param row массив строк из CSV
     * @return объект Book
     */
    private Book parseBookFromCsvRow(String[] row) {
        Book book = new Book();

        book.setTitle(row.length > 1 ? row[1] : null);
        book.setAuthor(row.length > 2 ? row[2] : null);
        book.setGenre(row.length > 3 ? row[3] : null);
        book.setIsbn(row.length > 4 ? row[4] : null);

        // Тип книги
        if (row.length > 5 && row[5] != null && !row[5].isEmpty()) {
            try {
                String typeStr = row[5].trim().toUpperCase();
                if (typeStr.contains("PAPER") || typeStr.contains("БУМАЖН")) {
                    book.setType(BookType.PAPER);
                } else if (typeStr.contains("ELECTRONIC") || typeStr.contains("ЭЛЕКТРОН")) {
                    book.setType(BookType.ELECTRONIC);
                } else if (typeStr.contains("AUDIO") || typeStr.contains("АУДИО")) {
                    book.setType(BookType.AUDIO);
                } else {
                    book.setType(BookType.PAPER);
                }
            } catch (Exception e) {
                book.setType(BookType.PAPER);
            }
        }

        // Статус
        if (row.length > 6 && row[6] != null && !row[6].isEmpty()) {
            try {
                String statusStr = row[6].trim().toUpperCase();
                if (statusStr.contains("READ") || statusStr.contains("ПРОЧИТ")) {
                    book.setStatus(BookStatus.READ);
                } else if (statusStr.contains("WANT") || statusStr.contains("ПЛАН")) {
                    book.setStatus(BookStatus.WANT_TO_READ);
                } else if (statusStr.contains("PROGRESS") || statusStr.contains("ПРОЦЕСС")) {
                    book.setStatus(BookStatus.IN_PROGRESS);
                } else {
                    book.setStatus(BookStatus.WANT_TO_READ);
                }
            } catch (Exception e) {
                book.setStatus(BookStatus.WANT_TO_READ);
            }
        }

        // Рейтинг
        if (row.length > 7 && row[7] != null && !row[7].isEmpty()) {
            try {
                double rating = Double.parseDouble(row[7].trim());
                if (rating >= 0.5 && rating <= 5) {
                    book.setRating(rating);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Страницы
        if (row.length > 8 && row[8] != null && !row[8].isEmpty()) {
            try {
                book.setPageCount(Integer.parseInt(row[8].trim()));
            } catch (NumberFormatException ignored) {}
        }

        // Значения по умолчанию
        if (book.getStatus() == null) {
            book.setStatus(BookStatus.WANT_TO_READ);
        }
        if (book.getType() == null) {
            book.setType(BookType.PAPER);
        }

        book.setAddedDate(LocalDateTime.now());

        return book;
    }

    /**
     * Парсит книгу из строки Excel.
     * <p>
     * Ожидаемый порядок колонок:
     * 0 - ID (игнорируется), 1 - Название, 2 - Автор, 3 - Жанр, 4 - ISBN,
     * 5 - Тип, 6 - Статус, 7 - Рейтинг, 8 - Страниц
     * </p>
     *
     * @param row строка Excel
     * @return объект Book
     */
    private Book parseBookFromExcelRow(Row row) {
        Book book = new Book();

        book.setTitle(getCellValueAsString(row, 1));
        book.setAuthor(getCellValueAsString(row, 2));
        book.setGenre(getCellValueAsString(row, 3));
        book.setIsbn(getCellValueAsString(row, 4));

        // Тип книги
        String typeStr = getCellValueAsString(row, 5);
        if (typeStr != null && !typeStr.isEmpty()) {
            try {
                String upperType = typeStr.trim().toUpperCase();
                if (upperType.contains("PAPER") || upperType.contains("БУМАЖН")) {
                    book.setType(BookType.PAPER);
                } else if (upperType.contains("ELECTRONIC") || upperType.contains("ЭЛЕКТРОН")) {
                    book.setType(BookType.ELECTRONIC);
                } else if (upperType.contains("AUDIO") || upperType.contains("АУДИО")) {
                    book.setType(BookType.AUDIO);
                } else {
                    book.setType(BookType.PAPER);
                }
            } catch (Exception e) {
                book.setType(BookType.PAPER);
            }
        }

        // Статус
        String statusStr = getCellValueAsString(row, 6);
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                String upperStatus = statusStr.trim().toUpperCase();
                if (upperStatus.contains("READ") || upperStatus.contains("ПРОЧИТ")) {
                    book.setStatus(BookStatus.READ);
                } else if (upperStatus.contains("WANT") || upperStatus.contains("ПЛАН")) {
                    book.setStatus(BookStatus.WANT_TO_READ);
                } else if (upperStatus.contains("PROGRESS") || upperStatus.contains("ПРОЦЕСС")) {
                    book.setStatus(BookStatus.IN_PROGRESS);
                } else {
                    book.setStatus(BookStatus.WANT_TO_READ);
                }
            } catch (Exception e) {
                book.setStatus(BookStatus.WANT_TO_READ);
            }
        }

        // Рейтинг
        Double rating = getCellValueAsDouble(row, 7);
        if (rating != null && rating >= 0.5 && rating <= 5) {
            book.setRating(rating);
        }

        // Страницы
        Integer pages = getCellValueAsInteger(row);
        if (pages != null && pages > 0) {
            book.setPageCount(pages);
        }

        // Значения по умолчанию
        if (book.getStatus() == null) {
            book.setStatus(BookStatus.WANT_TO_READ);
        }
        if (book.getType() == null) {
            book.setType(BookType.PAPER);
        }

        book.setAddedDate(LocalDateTime.now());

        return book;
    }

    /**
     * Проверяет, существует ли уже книга в библиотеке.
     *
     * @param book книга для проверки
     * @param skippedBooks список пропущенных книг (для записи причины)
     * @return true, если книга существует (должна быть пропущена)
     */
    private boolean isBookExists(Book book, List<String> skippedBooks) {
        // Проверка по ISBN
        if (book.getIsbn() != null && !book.getIsbn().isEmpty()) {
            if (bookService.getBookByIsbn(book.getIsbn()) != null) {
                skippedBooks.add("Книга с ISBN " + book.getIsbn() + " уже существует: " + book.getTitle());
                return true;
            }
        }

        // Проверка по названию и автору
        List<Book> existingByTitle = bookService.searchByTitle(book.getTitle());
        boolean titleAuthorMatch = existingByTitle.stream()
                .anyMatch(b -> b.getAuthor().equalsIgnoreCase(book.getAuthor()));
        if (titleAuthorMatch) {
            skippedBooks.add("Книга с таким названием и автором уже существует: " +
                    book.getTitle() + " - " + book.getAuthor());
            return true;
        }

        return false;
    }

    /**
     * Устанавливает результат импорта в модель.
     *
     * @param model модель для передачи атрибутов
     * @param successfullyImported список успешно импортированных книг
     * @param skippedBooks список пропущенных книг
     * @param errors список ошибок
     */
    private void setImportResult(Model model, List<Book> successfullyImported,
                                 List<String> skippedBooks, List<String> errors) {
        model.addAttribute("importSuccess", successfullyImported.size());
        model.addAttribute("importSkipped", skippedBooks.size());
        model.addAttribute("importErrors", errors.size());
        model.addAttribute("skippedBooksList", skippedBooks);
        model.addAttribute("errorsList", errors);
        model.addAttribute("importedBooks", successfullyImported);

        logger.info("Импорт завершён. Добавлено: {}, Пропущено: {}, Ошибок: {}",
                successfullyImported.size(), skippedBooks.size(), errors.size());
    }

    /**
     * Безопасное извлечение строки из карты.
     *
     * @param map карта с данными
     * @param key ключ
     * @return строковое значение или null
     */
    private String getStringFromMap(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Извлекает строковое значение из ячейки Excel.
     *
     * @param row строка Excel
     * @param index индекс ячейки
     * @return строковое значение или null
     */
    private String getCellValueAsString(Row row, int index) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(index);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    /**
     * Извлекает числовое значение (Double) из ячейки Excel.
     *
     * @param row строка Excel
     * @param index индекс ячейки
     * @return числовое значение или null
     */
    private Double getCellValueAsDouble(Row row, int index) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(index);
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    /**
     * Извлекает целочисленное значение из ячейки Excel.
     *
     * @param row строка Excel
     * @return целочисленное значение или null
     */
    private Integer getCellValueAsInteger(Row row) {
        Double value = getCellValueAsDouble(row, 8);
        return value != null ? value.intValue() : null;
    }
}