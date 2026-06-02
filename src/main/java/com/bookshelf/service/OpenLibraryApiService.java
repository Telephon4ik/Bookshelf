package com.bookshelf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookshelf.model.Book;
import com.bookshelf.model.BookType;
import com.bookshelf.model.BookStatus;
import com.bookshelf.util.IsbnUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для взаимодействия с Open Library API.
 * <p>
 * Предоставляет методы для поиска книг по текстовому запросу и по ISBN.
 * Open Library — это бесплатный открытый каталог книг, не требующий API ключа.
 * </p>
 * <p>
 * <strong>Особенности API:</strong>
 * <ul>
 *   <li>Не требует аутентификации</li>
 *   <li>Имеет ограничение на количество запросов (рекомендуется не более 1 запроса в секунду)</li>
 *   <li>Поддерживает поиск по ISBN, названию, автору</li>
 * </ul>
 * </p>
 *
 * @author Bookshelf Team
 * @version 2.1
 */
@Service
public class OpenLibraryApiService {

    private static final Logger logger = LoggerFactory.getLogger(OpenLibraryApiService.class);

    // API endpoints
    private static final String OPEN_LIBRARY_SEARCH_URL = "https://openlibrary.org/search.json?q=";
    private static final String OPEN_LIBRARY_ISBN_URL = "https://openlibrary.org/api/books?bibkeys=ISBN:";
    private static final String OPEN_LIBRARY_COVER_URL = "https://covers.openlibrary.org/b/id/";

    // Cover size suffixes
    private static final String COVER_SIZE_MEDIUM = "-M.jpg";
    private static final String COVER_SIZE_LARGE = "-L.jpg";

    // Limits
    private static final int MAX_RESULTS = 20;
    private static final int MAX_GENRE_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    // Timeouts (ms)
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Конструктор, инициализирующий RestTemplate с таймаутами.
     */
    public OpenLibraryApiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        logger.debug("OpenLibraryApiService инициализирован");
    }

    /**
     * Выполняет поиск книг в Open Library по текстовому запросу.
     *
     * @param query поисковый запрос (название книги, автор или их комбинация)
     * @return список найденных книг (максимум {@value #MAX_RESULTS} результатов)
     * @throws RestClientException если произошла ошибка при вызове API
     * @throws Exception           если произошла ошибка при обработке JSON-ответа
     */
    public List<Book> searchBooks(String query) throws Exception {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("Попытка поиска с пустым запросом");
            return new ArrayList<>();
        }

        logger.info("Поиск книг в Open Library: '{}'", query);

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = OPEN_LIBRARY_SEARCH_URL + encodedQuery + "&limit=" + MAX_RESULTS;

        logger.debug("Запрос к Open Library: {}", url);
        String response = restTemplate.getForObject(url, String.class);

        if (response == null) {
            logger.warn("Получен пустой ответ от Open Library для запроса: '{}'", query);
            return new ArrayList<>();
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode docs = root.get("docs");

        List<Book> books = new ArrayList<>();

        if (docs != null && docs.isArray()) {
            int docsSize = docs.size();
            logger.debug("Open Library вернул {} результатов", docsSize);

            int processedCount = 0;
            for (JsonNode doc : docs) {
                if (processedCount >= MAX_RESULTS) break;

                try {
                    Book book = parseBookFromDoc(doc);
                    if (book != null) {
                        books.add(book);
                        processedCount++;
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при обработке документа Open Library: {}", e.getMessage());
                }
            }
        }

        long booksWithIsbn = books.stream().filter(b -> b.getIsbn() != null && !b.getIsbn().isEmpty()).count();
        long booksWithCover = books.stream().filter(b -> b.getCoverUrl() != null && !b.getCoverUrl().isEmpty()).count();

        logger.info("Результат поиска '{}': найдено {} книг (с ISBN: {}, с обложками: {})",
                query, books.size(), booksWithIsbn, booksWithCover);

        return books;
    }

    /**
     * Парсит JSON-узел документа в объект Book.
     *
     * @param doc JSON-узел документа из поиска
     * @return объект Book с заполненными полями, или null если документ некорректен
     */
    private Book parseBookFromDoc(JsonNode doc) {
        if (doc == null) {
            logger.warn("Попытка парсинга null документа");
            return null;
        }

        Book book = new Book();

        // Название
        String title = doc.has("title") ? doc.get("title").asText() : "Unknown";
        book.setTitle(title);

        // Автор
        if (doc.has("author_name") && doc.get("author_name").isArray() && !doc.get("author_name").isEmpty()) {
            book.setAuthor(doc.get("author_name").get(0).asText());
        } else {
            book.setAuthor("Unknown Author");
        }

        // Жанр / Тема
        if (doc.has("subject") && doc.get("subject").isArray() && !doc.get("subject").isEmpty()) {
            String genre = doc.get("subject").get(0).asText();
            if (genre.length() > MAX_GENRE_LENGTH) {
                genre = genre.substring(0, MAX_GENRE_LENGTH - 3) + "...";
            }
            book.setGenre(genre);
        } else {
            book.setGenre("Uncategorized");
        }

        // ISBN
        String isbn = extractIsbnFromDoc(doc);
        if (isbn != null && !isbn.isEmpty()) {
            book.setIsbn(IsbnUtils.normalize(isbn));
        }

        // Обложка
        String coverUrl = extractCoverUrlFromDoc(doc);
        if (coverUrl != null && !coverUrl.isEmpty()) {
            book.setCoverUrl(coverUrl);
        }

        // Количество страниц
        if (doc.has("number_of_pages_median")) {
            book.setPageCount(doc.get("number_of_pages_median").asInt());
        } else if (doc.has("number_of_pages")) {
            book.setPageCount(doc.get("number_of_pages").asInt());
        }

        // Описание
        String description = extractDescription(doc);
        book.setDescription(description);

        // Стандартные значения
        book.setType(BookType.PAPER);
        book.setStatus(BookStatus.WANT_TO_READ);
        book.setAddedDate(LocalDateTime.now());

        return book;
    }

    /**
     * Извлекает URL обложки из документа поиска Open Library.
     *
     * @param doc JSON-узел документа
     * @return URL обложки или null
     */
    private String extractCoverUrlFromDoc(JsonNode doc) {
        if (doc == null) return null;

        // Приоритет 1: cover_i
        if (doc.has("cover_i")) {
            int coverId = doc.get("cover_i").asInt();
            if (coverId > 0) {
                return OPEN_LIBRARY_COVER_URL + coverId + COVER_SIZE_MEDIUM;
            }
        }

        // Приоритет 2: cover_edition_key
        if (doc.has("cover_edition_key")) {
            String coverEdition = doc.get("cover_edition_key").asText();
            if (coverEdition != null && !coverEdition.isEmpty()) {
                return "https://covers.openlibrary.org/b/olid/" + coverEdition + COVER_SIZE_MEDIUM;
            }
        }

        // Приоритет 3: id_goodreads
        if (doc.has("id_goodreads") && doc.get("id_goodreads").isArray() && !doc.get("id_goodreads").isEmpty()) {
            String goodreadsId = doc.get("id_goodreads").get(0).asText();
            if (goodreadsId != null && !goodreadsId.isEmpty()) {
                return "https://covers.openlibrary.org/b/goodreads/" + goodreadsId + COVER_SIZE_MEDIUM;
            }
        }

        return null;
    }

    /**
     * Извлекает ISBN из документа поиска Open Library.
     *
     * @param doc JSON-узел документа
     * @return строка ISBN или null
     */
    private String extractIsbnFromDoc(JsonNode doc) {
        if (doc == null) return null;

        // Попытка получить ISBN из поля isbn
        if (doc.has("isbn") && doc.get("isbn").isArray() && !doc.get("isbn").isEmpty()) {
            String rawIsbn = doc.get("isbn").get(0).asText();
            if (rawIsbn != null) {
                String cleanIsbn = rawIsbn.replaceAll("[^0-9X]", "");

                if (cleanIsbn.length() == 10 || cleanIsbn.length() == 13) {
                    return cleanIsbn;
                }

                if (cleanIsbn.length() > 13 && cleanIsbn.substring(0, 13).matches("\\d{13}")) {
                    return cleanIsbn.substring(0, 13);
                }
            }
        }

        // Попытка получить ISBN из поля identifiers
        if (doc.has("identifiers")) {
            JsonNode identifiers = doc.get("identifiers");

            if (identifiers.has("isbn_13") && identifiers.get("isbn_13").isArray() && !identifiers.get("isbn_13").isEmpty()) {
                String isbn13 = identifiers.get("isbn_13").get(0).asText();
                if (isbn13 != null) {
                    String cleanIsbn13 = isbn13.replaceAll("[^0-9]", "");
                    if (cleanIsbn13.length() == 13) {
                        return cleanIsbn13;
                    }
                }
            }

            if (identifiers.has("isbn_10") && identifiers.get("isbn_10").isArray() && !identifiers.get("isbn_10").isEmpty()) {
                String isbn10 = identifiers.get("isbn_10").get(0).asText();
                if (isbn10 != null) {
                    String cleanIsbn10 = isbn10.replaceAll("[^0-9X]", "");
                    if (cleanIsbn10.length() == 10) {
                        return cleanIsbn10;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Формирует описание книги из данных документа Open Library.
     *
     * @param doc JSON-узел документа
     * @return строка с описанием книги
     */
    private String extractDescription(JsonNode doc) {
        if (doc == null) return "Информация из Open Library";

        StringBuilder desc = new StringBuilder();

        if (doc.has("title_suggest")) {
            desc.append("Название: ").append(doc.get("title_suggest").asText()).append("\n");
        } else if (doc.has("title")) {
            desc.append("Название: ").append(doc.get("title").asText()).append("\n");
        }

        if (doc.has("author_name") && doc.get("author_name").isArray() && !doc.get("author_name").isEmpty()) {
            desc.append("Автор: ").append(doc.get("author_name").get(0).asText()).append("\n");
        }

        if (doc.has("first_publish_year")) {
            desc.append("Год публикации: ").append(doc.get("first_publish_year").asInt()).append("\n");
        }

        if (doc.has("publisher") && doc.get("publisher").isArray() && !doc.get("publisher").isEmpty()) {
            desc.append("Издательство: ").append(doc.get("publisher").get(0).asText());
        }

        String result = desc.toString();
        if (result.isEmpty()) {
            result = "Информация из Open Library";
        }

        if (result.length() > MAX_DESCRIPTION_LENGTH) {
            result = result.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
        }

        return result;
    }

    /**
     * Выполняет поиск книги по ISBN в Open Library.
     *
     * @param isbn ISBN для поиска (может содержать дефисы и пробелы)
     * @return объект Book или null, если книга не найдена
     * @throws IllegalArgumentException если ISBN имеет некорректный формат
     * @throws RuntimeException        если произошла ошибка при вызове API
     */
    public Book searchByIsbn(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            logger.warn("Попытка поиска по пустому ISBN");
            throw new IllegalArgumentException("ISBN не может быть пустым");
        }

        logger.info("Поиск книги в Open Library по ISBN: '{}'", isbn);

        String cleanIsbn = isbn.replaceAll("[\\s-]", "");

        if (!cleanIsbn.matches("\\d{9}[\\dX]") && !cleanIsbn.matches("\\d{13}")) {
            logger.warn("Некорректный формат ISBN: '{}'", isbn);
            throw new IllegalArgumentException("Некорректный формат ISBN. " +
                    "Введите ISBN-10 или ISBN-13 (10 или 13 цифр).");
        }

        String url = OPEN_LIBRARY_ISBN_URL + cleanIsbn + "&format=json&jscmd=data";

        try {
            logger.debug("Запрос к Open Library по ISBN: {}", url);
            String response = restTemplate.getForObject(url, String.class);

            if (response == null || response.equals("{}")) {
                logger.info("Книга с ISBN {} не найдена в Open Library", cleanIsbn);
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode bookNode = root.get("ISBN:" + cleanIsbn);

            if (bookNode == null || bookNode.isNull()) {
                logger.info("Книга с ISBN {} не найдена в Open Library", cleanIsbn);
                return null;
            }

            Book book = parseBookFromNode(bookNode);
            if (book != null) {
                book.setIsbn(cleanIsbn);
                logger.info("Книга найдена: '{}' - '{}'", book.getTitle(), book.getAuthor());
                return book;
            }

            return null;

        } catch (RestClientException e) {
            logger.error("Ошибка вызова Open Library API для ISBN {}: {}", cleanIsbn, e.getMessage());
            throw new RuntimeException("Сервис Open Library временно недоступен. Пожалуйста, попробуйте позже.", e);
        } catch (Exception e) {
            logger.error("Ошибка обработки ответа Open Library для ISBN {}: {}", cleanIsbn, e.getMessage(), e);
            throw new RuntimeException("Ошибка при поиске книги в Open Library", e);
        }
    }

    /**
     * Парсит JSON-узел книги в объект Book.
     *
     * @param bookNode JSON-узел с информацией о книге (из запроса по ISBN)
     * @return объект Book с заполненными полями, или null если узел некорректен
     */
    private Book parseBookFromNode(JsonNode bookNode) {
        if (bookNode == null) {
            logger.warn("Попытка парсинга null узла книги");
            return null;
        }

        Book book = new Book();

        // Название
        String title = bookNode.has("title") ? bookNode.get("title").asText() : "Unknown";
        book.setTitle(title);

        // Автор
        if (bookNode.has("authors") && bookNode.get("authors").isArray() && !bookNode.get("authors").isEmpty()) {
            JsonNode author = bookNode.get("authors").get(0);
            book.setAuthor(author.has("name") ? author.get("name").asText() : "Unknown Author");
        } else {
            book.setAuthor("Unknown Author");
        }

        // Жанр / Тема
        if (bookNode.has("subjects") && bookNode.get("subjects").isArray() && !bookNode.get("subjects").isEmpty()) {
            String genre = bookNode.get("subjects").get(0).has("name") ?
                    bookNode.get("subjects").get(0).get("name").asText() : "Uncategorized";
            if (genre.length() > MAX_GENRE_LENGTH) {
                genre = genre.substring(0, MAX_GENRE_LENGTH - 3) + "...";
            }
            book.setGenre(genre);
        } else {
            book.setGenre("Uncategorized");
        }

        // Количество страниц
        if (bookNode.has("number_of_pages")) {
            book.setPageCount(bookNode.get("number_of_pages").asInt());
        } else if (bookNode.has("pages")) {
            book.setPageCount(bookNode.get("pages").asInt());
        }

        // Описание
        String description = extractDescriptionFromNode(bookNode);
        book.setDescription(description);

        // Обложка
        String coverUrl = extractCoverUrlFromBookNode(bookNode);
        if (coverUrl != null && !coverUrl.isEmpty()) {
            book.setCoverUrl(coverUrl);
        }

        // Если жанр не определен, попробуем использовать издательство
        if ("Uncategorized".equals(book.getGenre()) && bookNode.has("publishers")) {
            JsonNode publishers = bookNode.get("publishers");
            if (publishers.isArray() && !publishers.isEmpty() && publishers.get(0).has("name")) {
                String publisher = publishers.get(0).get("name").asText();
                if (publisher != null && !publisher.isEmpty()) {
                    book.setGenre(publisher);
                }
            }
        }

        // Стандартные значения
        book.setType(BookType.PAPER);
        book.setStatus(BookStatus.WANT_TO_READ);
        book.setAddedDate(LocalDateTime.now());

        return book;
    }

    /**
     * Извлекает описание из узла книги Open Library.
     *
     * @param bookNode JSON-узел с информацией о книге
     * @return строка с описанием
     */
    private String extractDescriptionFromNode(JsonNode bookNode) {
        if (bookNode == null) return "Информация из Open Library";

        if (bookNode.has("description")) {
            JsonNode desc = bookNode.get("description");
            String description = desc.isTextual() ? desc.asText() : desc.toString();
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
            }
            return description;
        }
        return "Информация из Open Library";
    }

    /**
     * Извлекает URL обложки из узла книги Open Library.
     * <p>
     * Приоритет отдается: large > medium > small.
     * </p>
     *
     * @param bookNode JSON-узел с информацией о книге
     * @return URL обложки или null
     */
    private String extractCoverUrlFromBookNode(JsonNode bookNode) {
        if (bookNode == null) return null;

        // Приоритет 1: поле cover с размерами
        if (bookNode.has("cover")) {
            JsonNode cover = bookNode.get("cover");

            if (cover.has("large") && !cover.get("large").isNull()) {
                String largeUrl = cover.get("large").asText();
                if (largeUrl != null && !largeUrl.isEmpty()) {
                    return largeUrl;
                }
            }
            if (cover.has("medium") && !cover.get("medium").isNull()) {
                String mediumUrl = cover.get("medium").asText();
                if (mediumUrl != null && !mediumUrl.isEmpty()) {
                    return mediumUrl;
                }
            }
            if (cover.has("small") && !cover.get("small").isNull()) {
                String smallUrl = cover.get("small").asText();
                if (smallUrl != null && !smallUrl.isEmpty()) {
                    return smallUrl;
                }
            }
        }

        // Приоритет 2: поле covers
        if (bookNode.has("covers") && bookNode.get("covers").isArray() && !bookNode.get("covers").isEmpty()) {
            int coverId = bookNode.get("covers").get(0).asInt();
            if (coverId > 0) {
                return OPEN_LIBRARY_COVER_URL + coverId + COVER_SIZE_LARGE;
            }
        }

        // Приоритет 3: поле olid
        if (bookNode.has("olid")) {
            String olid = bookNode.get("olid").asText();
            if (olid != null && !olid.isEmpty()) {
                return "https://covers.openlibrary.org/b/olid/" + olid + COVER_SIZE_LARGE;
            }
        }

        return null;
    }
}