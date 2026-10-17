package io.github.aaronsamuvel.blueprints.pii.guardrails.inspect;

import java.util.List;

import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.ResponsePolicy;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Privacy boundary inspector. The caller is either the authenticated principal (when the app has
 * a Spring Security filter chain) or, for this local diagnostic, a simulated principal built from
 * {@code simulatedRoles}. Never expose this endpoint in production: the report shows raw values.
 */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyInspectorController {

    public record InspectRoundtripRequest(String message, List<String> documents, List<String> simulatedRoles,
                                          ResponsePolicy responsePolicy) {}

    private final PrivacyInspectorService service;

    public PrivacyInspectorController(PrivacyInspectorService service) {
        this.service = service;
    }

    @PostMapping("/inspect-roundtrip")
    public InspectionReport inspect(@RequestBody InspectRoundtripRequest r) {
        Authentication caller = r.simulatedRoles() != null
                ? UsernamePasswordAuthenticationToken.authenticated("inspector", null,
                        AuthorityUtils.createAuthorityList(r.simulatedRoles().toArray(String[]::new)))
                : SecurityContextHolder.getContext().getAuthentication();
        try {
            return service.inspect(new PrivacyInspectorService.InspectRequest(r.message(), r.documents(), r.responsePolicy()), caller);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
