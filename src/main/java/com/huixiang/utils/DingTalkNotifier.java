package com.huixiang.utils;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class DingTalkNotifier {

    @Value("${dingtalk.webhook:}")
    private String webhookUrl;

    private boolean enabled;

    @PostConstruct
    public void init() {
        enabled = webhookUrl != null && !webhookUrl.isEmpty();
        if (!enabled) {
            log.info("dingtalk webhook not configured, alert disabled");
        }
    }

    public void sendAlert(String title, String content) {
        if (!enabled) {
            log.warn("[DINGTALK-ALERT] {}: {}", title, content);
            return;
        }
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("msgtype", "markdown");
            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", String.format("## %s\n\n> %s\n\n- app: HuiXiang Life\n- time: %s",
                    title, content, java.time.LocalDateTime.now().toString()));
            message.put("markdown", markdown);

            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(JSON.toJSONBytes(message));
                os.flush();
            }
            int code = conn.getResponseCode();
            if (code == 200) {
                log.info("dingtalk alert sent: {}", title);
            } else {
                log.error("dingtalk alert failed, HTTP: {}", code);
            }
        } catch (Exception e) {
            log.error("dingtalk alert exception", e);
        }
    }
}
