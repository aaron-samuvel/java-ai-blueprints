package io.github.aaronsamuvel.blueprints.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import io.github.aaronsamuvel.blueprints.mcp.security.DevJwtMinter;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class DevJwtMinterTest {

    @Test
    void mintedTokenIsAcceptedByTheSameDecoderTheAppUses() throws Exception {
        String token = DevJwtMinter.mint(TestJwts.SECRET, "models.use models.premium", Instant.now());
        Jwt jwt = NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(TestJwts.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build().decode(token);
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("models.use models.premium");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void refusesMissingOrShortSecret() {
        assertThatThrownBy(() -> DevJwtMinter.mint(null, "models.use", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevJwtMinter.mint("too-short", "models.use", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
