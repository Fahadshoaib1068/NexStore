package com.example.store.controller;

import com.example.store.config.ApiAnalyticsService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final ApiAnalyticsService apiAnalyticsService;

    public AnalyticsController(ApiAnalyticsService apiAnalyticsService) {
        this.apiAnalyticsService = apiAnalyticsService;
    }

    @GetMapping("/timeline")
    public Map<String, Object> getTimeline() {
        return apiAnalyticsService.getTimeline();
    }

    @GetMapping("/hits")
    public Map<String, Object> getHits() {
        return Map.of(
                "totalRequests", apiAnalyticsService.getTotalRequests(),
                "totalApis", apiAnalyticsService.getTotalApis(),
                "avgResponseMs", Math.round(apiAnalyticsService.getAvgResponseMs()),
                "errorRate", Math.round(apiAnalyticsService.getErrorRate() * 10.0)/10.0,
                "hits", apiAnalyticsService.getHits()
        );
    }
}