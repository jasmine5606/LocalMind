package com.huixiang.config;

import com.huixiang.service.IUserInfoService;
import com.huixiang.utils.LoginInterceptor;
import com.huixiang.utils.RefreshTokenInterceptor;
import com.huixiang.utils.TraceIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserInfoService userInfoService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceIdInterceptor())
                .addPathPatterns("/**").order(-1);
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate, userInfoService))
                .addPathPatterns("/**").order(0);
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/chat/**"
                ).order(1);
    }
}
