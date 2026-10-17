package io.github.aaronsamuvel.blueprints.pii.web;

import java.time.Duration;

import io.github.aaronsamuvel.blueprints.pii.PrivacyChatService;
import io.github.aaronsamuvel.blueprints.shared.AiAnswer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record ChatRequest(String message) {}

    private final PrivacyChatService service;

    public ChatController(PrivacyChatService service) {
        this.service = service;
    }

    @PostMapping
    public AiAnswer chat(@RequestBody ChatRequest request) {
        long t = System.nanoTime();
        String answer = service.chat(request.message());
        return new AiAnswer(answer, "configured-chat-model", Duration.ofNanos(System.nanoTime() - t));
    }
}
