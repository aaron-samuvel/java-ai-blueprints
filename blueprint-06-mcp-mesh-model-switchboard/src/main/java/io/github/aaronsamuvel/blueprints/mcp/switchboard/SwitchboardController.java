package io.github.aaronsamuvel.blueprints.mcp.switchboard;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwitchboardController {

    public record AskRequest(TaskType task, String message) {}

    private final ModelSwitchboard switchboard;

    public SwitchboardController(ModelSwitchboard switchboard) {
        this.switchboard = switchboard;
    }

    @PostMapping("/api/switchboard/ask")
    public ModelSwitchboard.Routed ask(@AuthenticationPrincipal Jwt jwt, @RequestBody AskRequest req) {
        Set<String> scopes = scopes(jwt);
        return switchboard.ask(req.task(), scopes, req.message());
    }

    static Set<String> scopes(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        return scope == null ? Set.of() : Set.of(scope.split(" ")).stream().collect(Collectors.toUnmodifiableSet());
    }
}
