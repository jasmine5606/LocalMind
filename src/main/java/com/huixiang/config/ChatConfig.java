package com.huixiang.config;

import com.alibaba.dashscope.aigc.generation.Generation;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "bailian")
public class ChatConfig {
    private String apiKey;
    private String appId;

    @Bean
    public Generation generation() {
        return new Generation();
    }
}
