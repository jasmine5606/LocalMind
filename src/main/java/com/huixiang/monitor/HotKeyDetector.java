package com.huixiang.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HotKeyDetector {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheManager caffeineCacheManager;

    private static final long PROMOTION_THRESHOLD = 50L;

    private final Map<String, AtomicLong> localCounters = new ConcurrentHashMap<>();

    public void recordAccess(String key) {
        localCounters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    @Scheduled(fixedRate = 300000)
    public void detectAndPromoteHotKeys() {
        List<String> hotKeys = findHotKeys();
        if (hotKeys.isEmpty()) return;
        promoteToCaffeine(hotKeys);
        localCounters.clear();
    }

    private List<String> findHotKeys() {
        return localCounters.entrySet().stream()
                .filter(e -> e.getValue().get() >= PROMOTION_THRESHOLD)
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(20)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void promoteToCaffeine(List<String> hotKeys) {
        Cache<Object, Object> nativeCache = getCaffeineNativeCache();
        if (nativeCache == null) return;
        for (String key : hotKeys) {
            try {
                Object existing = nativeCache.getIfPresent(key);
                if (existing == null) {
                    String redisJson = stringRedisTemplate.opsForValue().get(key);
                    if (redisJson != null) {
                        nativeCache.put(key, redisJson);
                        log.info("Hot key promoted to Caffeine: {}", key);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to promote hot key: {}", key, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Cache<Object, Object> getCaffeineNativeCache() {
        try {
            org.springframework.cache.Cache cache = caffeineCacheManager.getCache("shop");
            if (cache instanceof CaffeineCache) {
                return ((CaffeineCache) cache).getNativeCache();
            }
        } catch (Exception e) {
            log.warn("Failed to get Caffeine native cache", e);
        }
        return null;
    }

    public Map<String, Long> getCurrentHotKeys() {
        return localCounters.entrySet().stream()
                .filter(e -> e.getValue().get() >= PROMOTION_THRESHOLD)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }
}
