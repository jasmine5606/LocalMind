package com.huixiang.service;

import com.huixiang.dto.UserDTO;
import com.huixiang.utils.UserHolder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService {

    @Resource
    private AgentTools agentTools;

    @Resource
    private BusinessIntelligenceTools biTools;

    @Resource
    private BlindBoxDesigner blindBoxDesigner;

    @Resource
    private ChatConfigService chatConfigService;

    private final Map<String, CustomerAgent> sessionAgents = new ConcurrentHashMap<>();
    private QwenChatModel chatModel;

    @PostConstruct
    public void init() {
        chatModel = QwenChatModel.builder()
                .apiKey(chatConfigService.getApiKey())
                .modelName("qwen-max")
                .build();
    }

    public String chat(String sessionId, String userMessage) {
        try {
            CustomerAgent agent = sessionAgents.computeIfAbsent(sessionId, id -> {
                return AiServices.builder(CustomerAgent.class)
                        .chatLanguageModel(chatModel)
                        .tools(agentTools, biTools, blindBoxDesigner)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                        .build();
            });

            String systemPrompt = buildSystemPrompt();
            String fullMessage = systemPrompt != null ? systemPrompt + "\n\n用户问题: " + userMessage : userMessage;

            String reply = agent.answer(fullMessage);
            return reply != null ? reply : "抱歉，我现在无法回答这个问题。";
        } catch (Exception e) {
            log.error("Agent chat error for session {}", sessionId, e);
            return "系统繁忙，请稍后再试。";
        }
    }

    private String buildSystemPrompt() {
        UserDTO user = UserHolder.getUser();
        if (user == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("[系统指令] 你是惠享生活圈的智能助手，帮助用户浏览本地商户、抢购优惠券、管理订单。");
        sb.append("回复应简洁友好，主动利用工具获取实时数据。");
        sb.append("当前用户: ").append(user.getNickName());
        if (user.isVip()) {
            sb.append("(VIP会员，优先处理)");
        }
        sb.append("。");
        return sb.toString();
    }

    public void clearSession(String sessionId) {
        sessionAgents.remove(sessionId);
    }
}
