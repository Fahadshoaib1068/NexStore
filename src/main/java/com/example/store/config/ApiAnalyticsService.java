package com.example.store.config;

import com.example.store.repository.ApiAnalyticsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApiAnalyticsService {

    private final ApiAnalyticsRepository repository;

    public ApiAnalyticsService(ApiAnalyticsRepository repository) {
        this.repository = repository;
    }

    private final Map<String, AtomicLong> hits     = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errors   = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> duration = new ConcurrentHashMap<>();

    private final Map<String, Long> lastSavedHits     = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSavedErrors   = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSavedDuration = new ConcurrentHashMap<>();

    private static final int TIMELINE_WINDOW_MINUTES = 10;
    private static final int BUCKET_SECONDS = 30;
    private static final int RETENTION_MINUTES = 15;

    public String resolveApiName(String uri) {
        String[] parts = uri.split("/");
        String base = parts.length > 1 ? parts[1] : "";
        return switch (base) {
            case "items"      -> "Item API";
            case "orders"     -> "Order API";
            case "users"      -> "User API";
            case "videos"     -> "Video API";
            case "auth"       -> "Auth API";
            case "analytics"  -> "Analytics API";
            default           -> null;
        };
    }

    public void record(String apiName, long durationMs, boolean isError) {
        System.out.println("Record API: " + apiName);
        hits.computeIfAbsent(apiName, k -> new AtomicLong()).incrementAndGet();
        duration.computeIfAbsent(apiName, k -> new AtomicLong()).addAndGet(durationMs);
        if (isError) errors.computeIfAbsent(apiName, k -> new AtomicLong()).incrementAndGet();

        try {
            repository.logCall(apiName, LocalDateTime.now());
        } catch (SQLException e) {
            System.out.println("Failed to log call for " + apiName);
            e.printStackTrace();
        }
    }

    // ─── used by the dashboard chart ─────────────────────────────
    public Map<String, Object> getTimeline() {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(TIMELINE_WINDOW_MINUTES);
        try {
            List<Map<String, Object>> buckets = repository.getTimeline(windowStart, BUCKET_SECONDS);
            return Map.of("windowStart", windowStart.toString(), "buckets", buckets);
        } catch (SQLException e) {
            System.out.println("Failed to load timeline");
            e.printStackTrace();
            return Map.of("windowStart", windowStart.toString(), "buckets", List.of());
        }
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    public void purgeOldCallLogs() {
        try {
            repository.deleteOlderThan(LocalDateTime.now().minusMinutes(RETENTION_MINUTES));
        } catch (SQLException e) {
            System.out.println("Failed to purge old call logs");
            e.printStackTrace();
        }
    }

    @PostConstruct
    public void loadFromDb() {
        Map<String, long[]> saved = repository.findAll();

        saved.forEach((name, values) -> {
            long h = values[0], e = values[1], d = values[2];

            hits.put(name, new AtomicLong(h));
            errors.put(name, new AtomicLong(e));
            duration.put(name, new AtomicLong(d));

            lastSavedHits.put(name, h);
            lastSavedErrors.put(name, e);
            lastSavedDuration.put(name, d);
        });

        System.out.println("Loaded saved API analytics from DB");
    }

    @Scheduled(fixedDelay = 30000)
    public void flushToDb() {
        for (String apiName : hits.keySet()) {
            long currentHits = hits.get(apiName).get();
            long currentErr  = errors.getOrDefault(apiName, new AtomicLong()).get();
            long currentDur  = duration.getOrDefault(apiName, new AtomicLong()).get();

            long prevHits = lastSavedHits.getOrDefault(apiName, 0L);
            long prevErr  = lastSavedErrors.getOrDefault(apiName, 0L);
            long prevDur  = lastSavedDuration.getOrDefault(apiName, 0L);

            long deltaHits = currentHits - prevHits;
            long deltaErr  = currentErr  - prevErr;
            long deltaDur  = currentDur  - prevDur;

            if (deltaHits == 0) continue;

            try {
                repository.addToCounts(apiName, deltaHits, deltaErr, deltaDur);

                lastSavedHits.put(apiName, currentHits);
                lastSavedErrors.put(apiName, currentErr);
                lastSavedDuration.put(apiName, currentDur);

            } catch (SQLException e) {
                System.out.println("Failed to save analytics for " + apiName);
                e.printStackTrace();
            }
        }
    }

    public Map<String, Long> getHits() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        hits.forEach((k, v) -> snapshot.put(k, v.get()));
        return snapshot;
    }

    public long getTotalRequests() {
        return hits.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public int getTotalApis() {
        return hits.size();
    }

    public double getAvgResponseMs() {
        long totalReq = getTotalRequests();
        long totalDur = duration.values().stream().mapToLong(AtomicLong::get).sum();
        return totalReq == 0 ? 0 : (double) totalDur / totalReq;
    }

    public double getErrorRate() {
        long totalReq = getTotalRequests();
        long totalErr = errors.values().stream().mapToLong(AtomicLong::get).sum();
        return totalReq == 0 ? 0 : (totalErr * 100.0) / totalReq;
    }
}