package com.bookshelf.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для ограничения частоты запросов (Rate Limiting).
 * <p>
 * Предотвращает злоупотребление API путём ограничения количества запросов
 * от одного IP-адреса в единицу времени.
 * </p>
 *
 * @author Bookshelf Team
 * @version 1.0
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    /**
     * Хранилище времени последнего запроса для каждого IP.
     */
    private final ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    /**
     * Хранилище количества запросов для каждого IP в текущую минуту.
     */
    private final ConcurrentHashMap<String, Integer> requestCount = new ConcurrentHashMap<>();

    /**
     * Минимальный интервал между запросами в миллисекундах.
     */
    private static final long MIN_REQUEST_INTERVAL_MS = 2000;

    /**
     * Максимальное количество запросов в минуту.
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    /**
     * Проверяет, превысил ли клиент лимит запросов.
     *
     * @param request HTTP-запрос клиента
     * @return true, если запрос должен быть отклонён (лимит превышен), иначе false
     */
    public boolean isRateLimited(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        long now = System.currentTimeMillis();

        Long lastRequest = lastRequestTime.get(clientIp);
        if (lastRequest != null && (now - lastRequest) < MIN_REQUEST_INTERVAL_MS) {
            logger.warn("Слишком частый запрос от IP: {}", clientIp);
            return true;
        }

        String minuteKey = clientIp + "_" + (now / 60000);
        Integer count = requestCount.get(minuteKey);
        if (count != null && count >= MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Превышен лимит запросов в минуту от IP: {}", clientIp);
            return true;
        }

        lastRequestTime.put(clientIp, now);
        requestCount.merge(minuteKey, 1, Integer::sum);

        if (requestCount.size() > 100) {
            requestCount.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                long timestamp = Long.parseLong(key.split("_")[1]);
                return (now - timestamp) > 60000;
            });
        }

        return false;
    }

    /**
     * Определяет реальный IP-адрес клиента с учётом прокси-серверов.
     *
     * @param request HTTP-запрос клиента
     * @return IP-адрес клиента
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}