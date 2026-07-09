package com.example.store.config;

import com.example.store.repository.ApiAnalyticsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
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

    // last saved
    private final Map<String, Long> lastSavedHits     = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSavedErrors   = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSavedDuration = new ConcurrentHashMap<>();

    // ─── recognize which API a URL belongs to ───────────────────
    public String resolveApiName(String uri) {
        String[] parts = uri.split("/");
        String base = parts.length > 1 ? parts[1] : "";
        return switch (base) {
            case "items"  -> "Item API";
            case "orders" -> "Order API";
            case "users"  -> "User API";
            case "videos" -> "Video API";
            case "auth"   -> "Auth API";
            default       -> null;
        };
    }

    // ─── called on every request ────────────────────────────────
    public void record(String apiName, long durationMs, boolean isError) {
        hits.computeIfAbsent(apiName, k -> new AtomicLong()).incrementAndGet();
        duration.computeIfAbsent(apiName, k -> new AtomicLong()).addAndGet(durationMs);
        if (isError) errors.computeIfAbsent(apiName, k -> new AtomicLong()).incrementAndGet();
    }

    // ─── load saved counts when the app starts ──────────────────
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

    // save after 30 sec
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

            if (deltaHits == 0) continue; // nothing new, skip

            try {
                repository.addToCounts(apiName, deltaHits, deltaErr, deltaDur);

//                lastSavedHits.put(apiName, currentHits);
//                lastSavedErrors.put(apiName, currentErr);
//                lastSavedDuration.put(apiName, currentDur);

            } catch (SQLException e) {
                System.out.println("Failed to save analytics for " + apiName);
                e.printStackTrace();
            }
        }
    }

    // ─── used by the dashboard ───────────────────────────────────
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