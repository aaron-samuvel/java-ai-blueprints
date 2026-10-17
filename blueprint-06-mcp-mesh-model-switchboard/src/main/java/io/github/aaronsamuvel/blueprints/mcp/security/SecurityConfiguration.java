package io.github.aaronsamuvel.blueprints.mcp.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * OAuth2 resource server. Every request needs a valid JWT:
 * <ul>
 *   <li>{@code /mcp/**} (the MCP endpoint) requires scope {@code mcp.tools};</li>
 *   <li>{@code /api/ops/**} (runtime OpenAPI bridge admin) requires scope {@code mcp.admin};</li>
 *   <li>{@code /actuator/health/**} is public (status only, for probes);</li>
 *   <li>{@code /api/switchboard/**} requires scope {@code models.use}; premium models also need {@code models.premium}.</li>
 * </ul>
 * Token validation is configured in one of two ways:
 * <ol>
 *   <li><b>Production:</b> {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (Boot auto-configures the decoder from the IdP).</li>
 *   <li><b>Local/dev and tests:</b> {@code blueprints.security.dev-jwt-secret} (HS256, at least 32 bytes), read from the
 *       {@code BLUEPRINTS_DEV_JWT_SECRET} environment variable. Never commit a value.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/mcp/**", "/mcp").hasAuthority("SCOPE_mcp.tools")
                        .requestMatchers("/api/ops/**").hasAuthority("SCOPE_mcp.admin")
                        .requestMatchers("/api/switchboard/**").hasAuthority("SCOPE_models.use")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @Conditional(DevSecretPresent.class)
    JwtDecoder devJwtDecoder(@Value("${blueprints.security.dev-jwt-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("blueprints.security.dev-jwt-secret must be at least 32 bytes");
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256")).build();
    }

    /** The dev decoder is on only when the secret is non-blank (application.properties defaults it to empty). */
    static final class DevSecretPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("blueprints.security.dev-jwt-secret"));
        }
    }
}
