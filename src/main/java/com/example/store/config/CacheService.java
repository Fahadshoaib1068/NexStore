package com.example.store.config;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Save to cache with 5 minute expiry
    public void put(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(5));
    }

    // Get from cache
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // ─── RATE LIMITING ──────────────────────────────────────────────
    public long incrementCounter(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // set expiry
            redisTemplate.expire(key, window);
        }
        return count != null ? count : 0;
    }

    // Delete from cache
    public void evict(String key) {
        redisTemplate.delete(key);
    }
}