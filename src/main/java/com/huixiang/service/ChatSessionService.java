package com.huixiang.service;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.fastjson.JSON;
import com.huixiang.dto.UserDTO;
import com.huixiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatSessionService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String SESSION_PREFIX = "chat:session:";
    private static final long SESSION_TTL = 30;
    private static final int MAX_MESSAGES = 20;

    public void addMessage(String sessionId, String role, String content) {
        String key = SESSION_PREFIX + sessionId;
        Map<String, String> msg = Map.of("role", role, "content", content);
        stringRedisTemplate.opsForList().rightPush(key, JSON.toJSONString(msg));
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
        stringRedisTemplate.expire(key, SESSION_TTL, TimeUnit.MINUTES);
    }

    public List<Message> getHistory(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        List<String> rawMessages = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<Message> history = new ArrayList<>();

        addSystemPrompt(history);

        if (rawMessages != null) {
            for (String raw : rawMessages) {
                try {
                    Map<?, ?> map = JSON.parseObject(raw, Map.class);
                    String role = String.valueOf(map.get("role"));
                    String content = String.valueOf(map.get("content"));
                    if ("function".equals(role)) {
                        history.add(Message.builder().role("function").content(content).build());
                    } else {
                        history.add(Message.builder().role(role).content(content).build());
                    }
                } catch (Exception e) {
                    log.warn("parse history message error: {}", e.getMessage());
                }
            }
        }
        return history;
    }

    private void addSystemPrompt(List<Message> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是惠享生活圈的智能助手，帮助用户浏览本地商户、抢购优惠券、管理订单。");
        UserDTO user = UserHolder.getUser();
        if (user != null) {
            prompt.append("当前用户: ").append(user.getNickName());
            if (user.isVip()) {
                prompt.append("(VIP会员)");
            }
            prompt.append("。");
        }
        prompt.append("回复应简洁友好，主动利用工具获取实时数据。");
        history.add(Message.builder().role(Role.SYSTEM.getValue()).content(prompt.toString()).build());
    }

    public void clearSession(String sessionId) {
        stringRedisTemplate.delete(SESSION_PREFIX + sessionId);
    }
}
