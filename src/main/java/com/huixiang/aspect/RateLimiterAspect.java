package com.huixiang.aspect;

import com.huixiang.annotation.RateLimiter;
import com.huixiang.utils.RedisConstants;
import com.huixiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> rateLimiterScript;

    @PostConstruct
    public void init() {
        rateLimiterScript = new DefaultRedisScript<>();
        rateLimiterScript.setLocation(new ClassPathResource("rate_limiter.lua"));
        rateLimiterScript.setResultType(Long.class);
    }

    @Around("@annotation(rateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter) throws Throwable {
        String identifier = buildIdentifier(rateLimiter);
        if (isBanned(identifier)) {
            throw new RuntimeException("操作违规，已被临时封禁，请5分钟后再试");
        }
        String key = buildKey(rateLimiter);
        Long allowed = stringRedisTemplate.execute(
                rateLimiterScript,
                Arrays.asList(key),
                String.valueOf(rateLimiter.timeWindow()),
                String.valueOf(rateLimiter.maxRequests())
        );
        if (allowed == null || allowed == 0) {
            if (rateLimiter.enableBan()) {
                trackViolation(identifier);
            }
            throw new RuntimeException(rateLimiter.message());
        }
        return joinPoint.proceed();
    }

    private boolean isBanned(String identifier) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(
                RedisConstants.RATE_LIMIT_BAN_PREFIX + identifier));
    }

    private void trackViolation(String identifier) {
        String violationKey = RedisConstants.RATE_LIMIT_VIOLATION_PREFIX + identifier;
        Long count = stringRedisTemplate.opsForValue().increment(violationKey);
        if (count == null) return;
        stringRedisTemplate.expire(violationKey, java.time.Duration.ofSeconds(
                RedisConstants.RATE_LIMIT_BAN_TTL_SECONDS));
        if (count >= RedisConstants.RATE_LIMIT_BAN_THRESHOLD) {
            String banKey = RedisConstants.RATE_LIMIT_BAN_PREFIX + identifier;
            stringRedisTemplate.opsForValue().set(banKey, "1",
                    RedisConstants.RATE_LIMIT_BAN_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            log.warn("IP/User {} banned due to repeated rate limit violations", identifier);
        }
    }

    private String buildIdentifier(RateLimiter rateLimiter) {
        switch (rateLimiter.dimension()) {
            case "ip": return "ip:" + getClientIp();
            case "user": return "user:" + (UserHolder.getUser() != null ? UserHolder.getUser().getId() : "unknown");
            default: return "global";
        }
    }

    private String buildKey(RateLimiter rateLimiter) {
        StringBuilder keyBuilder = new StringBuilder(rateLimiter.key());
        switch (rateLimiter.dimension()) {
            case "ip":
                keyBuilder.append("ip:").append(getClientIp());
                break;
            case "user":
                Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
                keyBuilder.append("user:").append(userId);
                break;
            case "global":
            default:
                keyBuilder.append("global");
        }
        return keyBuilder.toString();
    }

    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
