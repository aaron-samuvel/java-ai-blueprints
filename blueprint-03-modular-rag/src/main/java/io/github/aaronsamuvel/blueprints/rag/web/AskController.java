package io.github.aaronsamuvel.blueprints.rag.web;

import io.github.aaronsamuvel.blueprints.rag.ModularRagService;
import io.github.aaronsamuvel.blueprints.rag.RagAnswer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ask")
public class AskController {

    public record AskRequest(String question) {}

    private final ModularRagService rag;

    public AskController(ModularRagService rag) {
        this.rag = rag;
    }

    @PostMapping
    public RagAnswer ask(@RequestBody AskRequest request) {
        return rag.ask(request.question());
    }
}
