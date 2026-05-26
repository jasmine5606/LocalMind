package com.huixiang.controller;

import com.huixiang.dto.Result;
import com.huixiang.service.ChatService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/send")
    public Result sendMessage(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString().replace("-", ""));
        String message = body.get("message");
        if (message == null || message.isEmpty()) {
            return Result.fail("message is required");
        }

        String reply = chatService.chat(sessionId, message);

        Map<String, String> data = new java.util.HashMap<>();
        data.put("sessionId", sessionId);
        data.put("reply", reply);
        return Result.ok(data);
    }

    @DeleteMapping("/session/{sessionId}")
    public Result clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return Result.ok();
    }
}
