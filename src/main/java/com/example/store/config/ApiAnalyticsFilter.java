package com.example.store.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.*;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.io.IOException;

@Component
@Order(1) // Ensure this filter runs before the JwtFilter
public class ApiAnalyticsFilter implements Filter {

    private final ApiAnalyticsService apiAnalyticsService;

    public ApiAnalyticsFilter(ApiAnalyticsService analyticsService) {
        this.apiAnalyticsService = analyticsService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            String apiName = apiAnalyticsService.resolveApiName(req.getRequestURI());
            if (apiName != null) { // only track known API roots
                long duration = System.currentTimeMillis() - start;
                apiAnalyticsService.record(apiName, duration, res.getStatus() >= 400);
            }
            
        }
    }




}
