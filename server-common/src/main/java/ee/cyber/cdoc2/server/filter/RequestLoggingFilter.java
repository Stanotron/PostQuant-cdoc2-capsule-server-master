package ee.cyber.cdoc2.server.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logging for requests
 */
@Component
@Slf4j
public class RequestLoggingFilter implements Filter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest) {
            logRequestHeaders(httpRequest);
        }

        chain.doFilter(request, response);
    }

    private void logRequestHeaders(HttpServletRequest request) {
        Map<String, Object> logData = new LinkedHashMap<>();

        logData.put("method", request.getMethod());
        logData.put("uri", request.getRequestURI());

        putIfNotNull(logData, "contentType", request.getHeader("Content-Type"));
        putIfNotNull(logData, "origin", request.getHeader("Origin"));

        int contentLength = request.getContentLength();
        if (contentLength >= 0) {
            logData.put("contentLength", contentLength);
        }

        Map<String, String> secFetchHeaders = Collections.list(request.getHeaderNames())
            .stream()
            .filter(name -> name.toLowerCase().startsWith("sec-fetch-"))
            .collect(Collectors.toMap(
                name -> name,
                request::getHeader
            ));

        if (!secFetchHeaders.isEmpty()) {
            logData.put("secFetchHeaders", secFetchHeaders);
        }

        putIfNotNull(logData, "clientIp", getClientIp(request));

        try {
            log.info(OBJECT_MAPPER.writeValueAsString(logData));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize request log as JSON", e);
        }
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return null;
    }
}
