package com.huixiang.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String CACHE_DELETE_STREAM = "stream.cache.delete";
    public static final int CACHE_DELETE_MAX_RETRY = 3;
    public static final String HOT_KEY_COUNTER_PREFIX = "hot:key:counter:";
    public static final String RATE_LIMIT_VIOLATION_PREFIX = "rate:violation:";
    public static final String RATE_LIMIT_BAN_PREFIX = "rate:ban:";
    public static final long RATE_LIMIT_BAN_TTL_SECONDS = 300L;
    public static final int RATE_LIMIT_BAN_THRESHOLD = 3;
    public static final String KAFKA_DLQ_TOPIC = "canal-events-dlq";
    public static final int KAFKA_RETRY_MAX = 3;
    public static final String USER_INFO_KEY = "user:info:";
}
