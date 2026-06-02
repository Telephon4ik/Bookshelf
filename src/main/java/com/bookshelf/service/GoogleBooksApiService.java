package com.bookshelf.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookshelf.model.Book;
import com.bookshelf.model.BookType;
import com.bookshelf.model.BookStatus;
import com.bookshelf.util.IsbnUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для взаимодействия с Google Books API.
 * <p>
 * Предоставляет методы для поиска книг по текстовому запросу и по ISBN.
 * </p>
 * <p>
 * <strong>Важно:</strong> Для работы сервиса требуется API ключ Google Books,
 * который должен быть указан в application.properties:
 * <pre>google.books.api.key=ВАШ_API_КЛЮЧ</pre>
 * </p>
 *
 * @author Bookshelf Team
 * @version 2.0
 */
@Service
public class GoogleBooksApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleBooksApiService.class);
    private static final String GOOGLE_BOOKS_API_URL = "https://www.googleapis.com/books/v1/volumes?q=";

    /**
     * Значения-заглушки для не настроенного API ключа.
     * Используются для проверки, был ли ключ настроен пользователем.
     */
    private static final String UNCONFIGURED_API_KEY_PLACEHOLDER_1 = "???_?????????_API_????";
    private static final String UNCONFIGURED_API_KEY_PLACEHOLDER_2 = "ВАШ_API_КЛЮЧ_GOOGLE_BOOKS";

    /**
     * Максимальное количество результатов при поиске.
     */
    private static final int MAX_RESULTS = 20;

    /**
     * Максимальная длина описания (символов).
     */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /**
     * Таймаут подключения в миллисекундах.
     */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /**
     * Таймаут чтения в миллисекундах.
     */
    private static final int READ_TIMEOUT_MS = 10000;

    @Value("${google.books.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Конструктор, инициализирующий RestTemplate с таймаутами.
     */
    public GoogleBooksApiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
        logger.debug("GoogleBooksApiService инициализирован с таймаутами: connect={}ms, read={}ms",
                CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /**
     * Добавляет API ключ к URL, если он настроен.
     *
     * @param url исходный URL
     * @return URL с добавленным API ключом
     */
    private String addApiKey(String url) {
        if (isApiKeyConfigured()) {
            logger.debug("API ключ добавлен к запросу");
            return url + "&key=" + apiKey;
        }
        logger.warn("API ключ Google Books не настроен в application.properties! Запрос будет без ключа.");
        return url;
    }

    /**
     * Проверяет, настроен ли API ключ.
     * <p>
     * Ключ считается не настроенным, если он:
     * <ul>
     *   <li>равен null или пустой строке</li>
     *   <li>содержит значение-заглушку из application.properties по умолчанию</li>
     * </ul>
     * </p>
     *
     * @return true, если ключ настроен и может быть использован, иначе false
     */
    private boolean isApiKeyConfigured() {
        boolean isConfigured = apiKey != null && !apiKey.isEmpty() &&
                !UNCONFIGURED_API_KEY_PLACEHOLDER_1.equals(apiKey) &&
                !UNCONFIGURED_API_KEY_PLACEHOLDER_2.equals(apiKey);

        if (!isConfigured) {
            logger.warn("API ключ Google Books не настроен. " +
                    "Пожалуйста, получите ключ в Google Cloud Console и добавьте в application.properties");
        }

        return isConfigured;
    }

    /**
     * Выполняет поиск книг в Google Books по текстовому запросу.
     *
     * @param query поисковый запрос (название книги, автор или их комбинация)
     * @return список найденных книг (максимум {@value #MAX_RESULTS} результатов)
     * @throws RuntimeException если API ключ не настроен или произошла ошибка API
     */
    public List<Book> searchBooks(String query) {
        logger.info("Поиск книг в Google Books API по запросу: '{}'", query);

        if (!isApiKeyConfigured()) {
            logger.error("API ключ Google Books не настроен! Поиск невозможен.");
            throw new RuntimeException("API ключ Google Books не настроен. " +
                    "Пожалуйста, настройте google.books.api.key в application.properties");
        }

        if (query == null || query.trim().isEmpty()) {
            logger.warn("Пустой поисковый запрос");
            return new ArrayList<>();
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = GOOGLE_BOOKS_API_URL + encodedQuery + "&maxResults=" + MAX_RESULTS;
            url = addApiKey(url);

            logger.debug("Отправка запроса к Google Books API: {}", url.replaceAll("key=[^&]*", "key=***"));
            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                logger.warn("Пустой ответ от Google Books API для запроса: '{}'", query);
                return new ArrayList<>();
            }

            JsonNode root = objectMapper.readTree(response);

            if (root.has("error")) {
                JsonNode error = root.get("error");
                int errorCode = error.has("code") ? error.get("code").asInt() : 0;
                String errorMessage = error.has("message") ? error.get("message").asText() : "Unknown error";

                logger.error("Ошибка Google Books API: код={}, сообщение={}", errorCode, errorMessage);

                if (errorCode == 429) {
                    throw new RuntimeException("Превышена квота запросов Google Books API. Попробуйте завтра.");
                } else if (errorCode == 403) {
                    throw new RuntimeException("Ошибка доступа к Google Books API. Проверьте API ключ.");
                } else {
                    throw new RuntimeException("Ошибка Google Books API: " + errorMessage);
                }
            }

            JsonNode items = root.get("items");
            List<Book> books = new ArrayList<>();

            if (items != null && items.isArray()) {
                logger.debug("Найдено {} результатов в Google Books", items.size());
                int resultCount = 0;

                for (JsonNode item : items) {
                    if (resultCount++ >= MAX_RESULTS) break;

                    try {
                        JsonNode volumeInfo = item.get("volumeInfo");
                        if (volumeInfo != null) {
                            Book book = parseBookFromJson(volumeInfo);
                            books.add(book);
                        } else {
                            logger.debug("Пропущен элемент без volumeInfo");
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при обработке книги из Google Books: {}", e.getMessage());
                    }
                }
            }

            long booksWithIsbn = books.stream().filter(b -> b.getIsbn() != null).count();
            logger.info("Найдено {} книг в Google Books, из них с ISBN: {}",
                    books.size(), booksWithIsbn);

            return books;

        } catch (HttpClientErrorException.TooManyRequests e) {
            logger.error("Превышена квота запросов Google Books API (429)");
            throw new RuntimeException("Превышена квота запросов Google Books API. Попробуйте завтра.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            logger.error("Ошибка доступа к Google Books API (403)");
            throw new RuntimeException("Ошибка доступа к Google Books API. Проверьте API ключ.", e);
        } catch (RestClientException e) {
            logger.error("Ошибка при вызове Google Books API: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка подключения к Google Books API. Попробуйте позже.", e);
        } catch (Exception e) {
            logger.error("Ошибка обработки ответа от Google Books API: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка обработки данных от Google Books API", e);
        }
    }

    /**
     * Парсит JSON-узел volumeInfo в объект Book.
     *
     * @param volumeInfo JSON-узел с информацией о книге
     * @return объект Book с заполненными полями
     */
    private Book parseBookFromJson(JsonNode volumeInfo) {
        Book book = new Book();

        // Название
        String title = volumeInfo.has("title") ? volumeInfo.get("title").asText() : "Unknown";
        book.setTitle(title);

        // Автор
        if (volumeInfo.has("authors") && volumeInfo.get("authors").isArray() && !volumeInfo.get("authors").isEmpty()) {
            book.setAuthor(volumeInfo.get("authors").get(0).asText());
        } else {
            book.setAuthor("Unknown Author");
        }

        // Жанр / Категория
        if (volumeInfo.has("categories") && volumeInfo.get("categories").isArray() && !volumeInfo.get("categories").isEmpty()) {
            book.setGenre(volumeInfo.get("categories").get(0).asText());
        } else {
            book.setGenre("Uncategorized");
        }

        // Описание
        String description = volumeInfo.has("description") ? volumeInfo.get("description").asText() : "";
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
        }
        book.setDescription(description);

        // Обложка
        if (volumeInfo.has("imageLinks") && volumeInfo.get("imageLinks").has("thumbnail")) {
            book.setCoverUrl(volumeInfo.get("imageLinks").get("thumbnail").asText());
        }

        // Количество страниц
        book.setPageCount(volumeInfo.has("pageCount") ? volumeInfo.get("pageCount").asInt() : 0);

        // Стандартные значения
        book.setType(BookType.PAPER);
        book.setStatus(BookStatus.WANT_TO_READ);
        book.setAddedDate(LocalDateTime.now());

        // ISBN
        String isbn = extractIsbnFromVolumeInfo(volumeInfo);
        if (isbn != null) {
            book.setIsbn(IsbnUtils.normalize(isbn));
            logger.debug("Найден ISBN для книги '{}': {}", title, isbn);
        }

        return book;
    }

    /**
     * Извлекает ISBN из узла volumeInfo.
     * <p>
     * Приоритет отдается ISBN-13, затем ISBN-10.
     * </p>
     *
     * @param volumeInfo JSON-узел с информацией о книге
     * @return строка ISBN или null, если ISBN не найден
     */
    private String extractIsbnFromVolumeInfo(JsonNode volumeInfo) {
        if (!volumeInfo.has("industryIdentifiers")) {
            return null;
        }

        String isbn13 = null;
        String isbn10 = null;

        for (JsonNode identifier : volumeInfo.get("industryIdentifiers")) {
            String type = identifier.get("type").asText();
            String value = identifier.get("identifier").asText();

            // Очищаем от нецифровых символов
            String cleanValue = value.replaceAll("[^0-9X]", "");

            if ("ISBN_13".equals(type)) {
                if (cleanValue.length() == 13) {
                    isbn13 = cleanValue;
                } else if (cleanValue.length() > 13 && cleanValue.substring(0, 13).matches("\\d{13}")) {
                    // Некоторые API возвращают ISBN с дополнительными символами
                    isbn13 = cleanValue.substring(0, 13);
                }
            } else if ("ISBN_10".equals(type) && cleanValue.length() == 10) {
                isbn10 = cleanValue;
            }
        }

        // Возвращаем ISBN-13, если есть, иначе ISBN-10
        return isbn13 != null ? isbn13 : isbn10;
    }

    /**
     * Выполняет поиск книги по ISBN.
     *
     * @param isbn ISBN для поиска (может содержать дефисы и пробелы)
     * @return объект Book или null, если книга не найдена
     * @throws IllegalArgumentException если ISBN имеет некорректный формат
     * @throws RuntimeException        если API ключ не настроен или произошла ошибка API
     */
    public Book searchByIsbn(String isbn) {
        logger.info("Поиск книги по ISBN в Google Books: '{}'", isbn);

        if (!isApiKeyConfigured()) {
            logger.error("API ключ Google Books не настроен! Поиск по ISBN невозможен.");
            throw new RuntimeException("API ключ Google Books не настроен. " +
                    "Пожалуйста, настройте google.books.api.key в application.properties");
        }

        if (isbn == null || isbn.trim().isEmpty()) {
            logger.warn("Пустой ISBN для поиска");
            throw new IllegalArgumentException("ISBN не может быть пустым");
        }

        if (!IsbnUtils.isValid(isbn)) {
            logger.warn("Некорректный формат ISBN: '{}'", isbn);
            throw new IllegalArgumentException("Некорректный формат ISBN. " +
                    "Введите ISBN-10 или ISBN-13 (10 или 13 цифр).");
        }

        String cleanIsbn = IsbnUtils.normalize(isbn);
        logger.debug("Нормализованный ISBN: {}", cleanIsbn);

        try {
            String url = GOOGLE_BOOKS_API_URL + "isbn:" + cleanIsbn;
            url = addApiKey(url);

            logger.debug("Отправка запроса к Google Books API по ISBN");
            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                logger.warn("Пустой ответ от Google Books API для ISBN: {}", cleanIsbn);
                return null;
            }

            JsonNode root = objectMapper.readTree(response);

            if (root.has("error")) {
                JsonNode error = root.get("error");
                int errorCode = error.has("code") ? error.get("code").asInt() : 0;
                String errorMessage = error.has("message") ? error.get("message").asText() : "Unknown error";

                logger.error("Ошибка Google Books API при поиске по ISBN {}: код={}, сообщение={}",
                        cleanIsbn, errorCode, errorMessage);

                if (errorCode == 429) {
                    throw new RuntimeException("Превышена квота запросов Google Books API. Попробуйте завтра.");
                } else if (errorCode == 403) {
                    throw new RuntimeException("Ошибка доступа к Google Books API. Проверьте API ключ.");
                }
                return null;
            }

            JsonNode items = root.get("items");

            if (items != null && items.isArray() && !items.isEmpty()) {
                JsonNode volumeInfo = items.get(0).get("volumeInfo");
                if (volumeInfo != null) {
                    Book book = parseBookFromJson(volumeInfo);
                    // Убеждаемся, что ISBN установлен
                    book.setIsbn(cleanIsbn);
                    logger.info("Книга найдена по ISBN {}: '{}' - '{}'",
                            cleanIsbn, book.getTitle(), book.getAuthor());
                    return book;
                }
            }

            logger.info("Книга не найдена по ISBN: {}", cleanIsbn);
            return null;

        } catch (HttpClientErrorException.TooManyRequests e) {
            logger.error("Превышена квота запросов Google Books API для ISBN {}", cleanIsbn);
            throw new RuntimeException("Превышена квота запросов Google Books API. Попробуйте завтра.", e);
        } catch (HttpClientErrorException.Forbidden e) {
            logger.error("Ошибка доступа к Google Books API для ISBN {}", cleanIsbn);
            throw new RuntimeException("Ошибка доступа к Google Books API. Проверьте API ключ.", e);
        } catch (RestClientException e) {
            logger.error("Ошибка при вызове Google Books API для ISBN {}: {}", cleanIsbn, e.getMessage());
            throw new RuntimeException("Ошибка подключения к Google Books API. Попробуйте позже.", e);
        } catch (Exception e) {
            logger.error("Ошибка обработки ответа для ISBN {}: {}", cleanIsbn, e.getMessage(), e);
            throw new RuntimeException("Ошибка обработки данных от Google Books API", e);
        }
    }
}