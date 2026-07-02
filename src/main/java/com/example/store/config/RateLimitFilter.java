package com.example.store.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final CacheService cacheService;

    private static final int    MAX_REQUESTS = 10;
    private static final Duration WINDOW     = Duration.ofMinutes(1);

    public RateLimitFilter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit GET /items
        boolean isTargetEndpoint = request.getMethod().equals("GET")
                && request.getRequestURI().equals("/items");

        if (isTargetEndpoint) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Use username if logged in, otherwise IP address
            String identifier = (auth != null && auth.isAuthenticated())
                    ? auth.getName()
                    : request.getRemoteAddr();

            String key = "ratelimit:items:" + identifier;

            long count = cacheService.incrementCounter(key, WINDOW);

            System.out.println("Rate limit check for " + identifier + ": " + count + "/" + MAX_REQUESTS);

            if (count > MAX_REQUESTS) {
                response.setStatus(429); // Too Many Requests
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\": \"Rate limit exceeded. Max " + MAX_REQUESTS + " requests per minute.\"}"
                );
                return; // STOP — don't let request continue
            }
        }

        filterChain.doFilter(request, response);
    }
}