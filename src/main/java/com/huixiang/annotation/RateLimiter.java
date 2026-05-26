package com.huixiang.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {
    String key() default "rate:limit:";
    long timeWindow() default 60;
    long maxRequests() default 10;
    String dimension() default "ip";
    String message() default "操作过于频繁，请稍后再试";
    boolean enableBan() default false;
}
