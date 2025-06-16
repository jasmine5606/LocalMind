package com.huixiang.controller;

import com.huixiang.dto.Result;
import com.huixiang.service.AgentTools;
import com.huixiang.service.BlindBoxDesigner;
import com.huixiang.service.BusinessIntelligenceTools;
import com.huixiang.service.ChatConfigService;
import com.huixiang.service.ChatService;
import com.huixiang.utils.UserHolder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private AgentTools agentTools;

    @Resource
    private BusinessIntelligenceTools biTools;

    @Resource
    private ChatConfigService chatConfigService;

    @Resource
    private BlindBoxDesigner blindBoxDesigner;

    private final Map<String, StreamAssistant> streamSessions = new ConcurrentHashMap<>();

    interface StreamAssistant {
        TokenStream chat(String message);
    }

    @PostMapping("/send")
    public Result sendMessage(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString().replace("-", ""));
        String message = body.get("message");
        if (message == null || message.isEmpty()) return Result.fail("message required");
        String reply = chatService.chat(sessionId, message);
        Map<String, String> data = Map.of("sessionId", sessionId, "reply", reply != null ? reply : "");
        return Result.ok(data);
    }

    @GetMapping("/stream")
    public SseEmitter streamChat(@RequestParam String sessionId, @RequestParam String message) {
        SseEmitter emitter = new SseEmitter(120000L);
        StreamAssistant assistant = streamSessions.computeIfAbsent(sessionId, id ->
            AiServices.builder(StreamAssistant.class)
                .streamingChatLanguageModel(QwenStreamingChatModel.builder()
                    .apiKey(chatConfigService.getApiKey()).modelName("qwen-max").build())
                .tools(agentTools, biTools, blindBoxDesigner)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build()
        );
        String prompt = buildPrompt(message);
        assistant.chat(prompt)
            .onNext(token -> send(emitter, token))
            .onComplete(response -> { send(emitter, "[DONE]"); emitter.complete(); })
            .onError(emitter::completeWithError)
            .start();
        return emitter;
    }

    @DeleteMapping("/session/{sessionId}")
    public String clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        streamSessions.remove(sessionId);
        return "ok";
    }

    private String buildPrompt(String message) {
        if (UserHolder.getUser() != null) {
            return String.format("[当前用户: %s%s] %s",
                UserHolder.getUser().getNickName(),
                UserHolder.getUser().isVip() ? "(VIP)" : "", message);
        }
        return message;
    }

    private void send(SseEmitter emitter, String data) {
        try { emitter.send(SseEmitter.event().data(data)); } catch (IOException ignored) {}
    }
}
