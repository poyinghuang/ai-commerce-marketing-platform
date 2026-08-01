package com.aicommerce.platform.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
