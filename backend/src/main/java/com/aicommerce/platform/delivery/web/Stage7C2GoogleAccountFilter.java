package com.aicommerce.platform.delivery.web;

import com.aicommerce.platform.delivery.application.PlatformWebAccountLane;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage4b.enabled:false}' == 'true' && '${platform.stage7.google.web.enabled:false}' == 'true'")
final class Stage7C2GoogleAccountFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/platforms/google/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        PlatformWebAccountLane.set(PlatformWebAccountLane.GOOGLE);
        try {
            chain.doFilter(request, response);
        } finally {
            PlatformWebAccountLane.clear();
        }
    }
}
