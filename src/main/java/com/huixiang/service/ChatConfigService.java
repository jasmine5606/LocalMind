package com.huixiang.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bailian")
public class ChatConfigService {
    private String apiKey;
    private String appId;
}
