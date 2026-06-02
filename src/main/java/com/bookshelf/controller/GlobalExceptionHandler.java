package com.bookshelf.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Глобальный обработчик исключений для приложения Bookshelf.
 * <p>
 * Перехватывает исключения, возникающие в контроллерах, и возвращает
 * пользователю понятные сообщения об ошибках с соответствующим HTTP-статусом.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Обрабатывает все необработанные исключения, которые не были перехвачены
     * более специфичными обработчиками.
     * <p>
     * Этот метод является "catch-all" и должен использоваться только для
     * действительно непредвиденных ошибок. Возвращает HTTP статус 500.
     * </p>
     *
     * @param e исключение (любое, не перехваченное другими обработчиками)
     * @return объект ModelAndView с представлением ошибки
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleGenericException(Exception e) {
        logger.error("Произошла непредвиденная ошибка: ", e);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "Произошла ошибка: " + e.getMessage());
        mav.addObject("status", 500);
        return mav;
    }

    /**
     * Обрабатывает ошибки при вызове внешних API (Google Books, Open Library).
     * <p>
     * Возвращает HTTP статус 503 (Service Unavailable).
     * </p>
     *
     * @param e исключение RestClientException
     * @return объект ModelAndView с представлением ошибки
     */
    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ModelAndView handleApiException(RestClientException e) {
        logger.error("Ошибка при вызове внешнего API: ", e);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "Ошибка подключения к сервису книг. Пожалуйста, попробуйте позже.");
        mav.addObject("status", 503);
        return mav;
    }

    /**
     * Обрабатывает ошибки валидации данных (IllegalArgumentException).
     * <p>
     * Возникает при передаче некорректных параметров или нарушении бизнес-правил.
     * Возвращает HTTP статус 400 (Bad Request).
     * </p>
     *
     * @param e исключение IllegalArgumentException
     * @return объект ModelAndView с представлением ошибки
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Ошибка валидации: {}", e.getMessage());

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "Ошибка в данных: " + e.getMessage());
        mav.addObject("status", 400);
        return mav;
    }

    /**
     * Обрабатывает ошибки целостности данных (например, дублирование ISBN).
     * <p>
     * Возвращает HTTP статус 409 (Conflict).
     * </p>
     *
     * @param e исключение DataIntegrityViolationException
     * @return объект ModelAndView с представлением ошибки
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ModelAndView handleDataIntegrity(DataIntegrityViolationException e) {
        logger.error("Ошибка целостности данных: ", e);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "Книга с таким ISBN уже существует в библиотеке");
        mav.addObject("status", 409);
        return mav;
    }

    /**
     * Обрабатывает ошибки, когда запрашиваемая страница не найдена.
     * <p>
     * Возвращает HTTP статус 404 (Not Found).
     * </p>
     *
     * @param e исключение NoHandlerFoundException
     * @return объект ModelAndView с представлением ошибки
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNotFound(NoHandlerFoundException e) {
        logger.warn("Страница не найдена: {}", e.getRequestURL());

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", "Страница не найдена");
        mav.addObject("status", 404);
        return mav;
    }
}