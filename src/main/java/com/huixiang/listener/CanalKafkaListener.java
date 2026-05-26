package com.huixiang.listener;

import com.alibaba.fastjson.JSON;
import com.huixiang.utils.DingTalkNotifier;
import com.huixiang.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class CanalKafkaListener {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private DingTalkNotifier dingTalkNotifier;

    private static final Map<String, String> TABLE_CACHE_KEY_MAP = new HashMap<>();

    static {
        TABLE_CACHE_KEY_MAP.put("tb_voucher", "cache:voucher:");
        TABLE_CACHE_KEY_MAP.put("tb_shop", "cache:shop:");
        TABLE_CACHE_KEY_MAP.put("tb_user", "cache:user:");
        TABLE_CACHE_KEY_MAP.put("tb_blog", "cache:blog:");
        TABLE_CACHE_KEY_MAP.put("tb_voucher_order", "order:");
    }

    @KafkaListener(topics = "canal-events", groupId = "huixiang-cache-group")
    public void handleCanalEvent(String message, Acknowledgment acknowledgment) {
        processWithRetry(message, 0, acknowledgment);
    }

    @KafkaListener(topics = "${kafka.dlq.topic:canal-events-dlq}", groupId = "huixiang-cache-dlq-group")
    public void handleDlqEvent(String message, Acknowledgment acknowledgment) {
        try {
            CanalMessage canalMessage = JSON.parseObject(message, CanalMessage.class);
            String alertMsg = String.format("cache sync failed\n table: %s\n event: %s",
                    canalMessage.getTableName(), canalMessage.getEventType());
            dingTalkNotifier.sendAlert("cache sync alert", alertMsg);
            log.error("DLQ message processed, alert sent: {}", message);
        } catch (Exception e) {
            log.error("DLQ processing error", e);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private void processWithRetry(String message, int attempt, Acknowledgment acknowledgment) {
        try {
            CanalMessage canalMessage = JSON.parseObject(message, CanalMessage.class);
            String tableName = canalMessage.getTableName();
            log.debug("processing cache eviction: table={}, attempt={}", tableName, attempt);
            String cacheKeyPrefix = TABLE_CACHE_KEY_MAP.get(tableName);
            if (cacheKeyPrefix != null) {
                deleteCacheByPrefix(cacheKeyPrefix);
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("cache eviction error, attempt={}", attempt, e);
            if (attempt < RedisConstants.KAFKA_RETRY_MAX) {
                int delayMs = (int) (Math.pow(2, attempt) * 10000);
                log.warn("retry {} failed, sleeping {}ms", attempt + 1, delayMs);
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                processWithRetry(message, attempt + 1, acknowledgment);
            } else {
                sendToDlq(message);
                acknowledgment.acknowledge();
            }
        }
    }

    private void sendToDlq(String message) {
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send("canal-events-dlq", message).get(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
                log.error("max retries reached, sent to DLQ: {}", message);
            } catch (Exception e) {
                log.error("DLQ send failed, fallback to compensation", e);
                sendCompensationMessage(message);
            }
        } else {
            sendCompensationMessage(message);
        }
    }

    private void sendCompensationMessage(String message) {
        try {
            Map<String, Object> compMessage = new HashMap<>();
            compMessage.put("key", "canal:failed:" + System.currentTimeMillis());
            compMessage.put("retryCount", "0");
            compMessage.put("rawMessage", message);
            RecordId recordId = stringRedisTemplate.opsForStream().add(
                    StreamRecords.newRecord().ofObject(compMessage)
                            .withStreamKey(RedisConstants.CACHE_DELETE_STREAM));
            log.info("compensation message sent to Redis Stream, id: {}", recordId);
        } catch (Exception e) {
            log.error("compensation send failed", e);
            dingTalkNotifier.sendAlert("fatal cache sync error",
                    "cache eviction failed, DLQ and compensation both failed\n" + message);
        }
    }

    private void deleteCacheByPrefix(String prefix) {
        stringRedisTemplate.delete(stringRedisTemplate.keys(prefix + "*"));
    }

    static class CanalMessage {
        private String tableName;
        private String eventType;
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
    }
}
