package io.github.aaronsamuvel.blueprints.mcp.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Local-development helper: mints an HS256 JWT that the dev decoder in {@link SecurityConfiguration} accepts,
 * so you can call the running node by hand without pasting the secret into a website.
 * <p>
 * The secret is read only from the {@code BLUEPRINTS_DEV_JWT_SECRET} environment variable (never from arguments,
 * so it does not end up in shell history). Arguments are the scopes, for example {@code models.use models.premium}.
 * Tokens expire after one hour. Not for production: use a real identity provider there.
 */
public final class DevJwtMinter {

    static final Duration LIFETIME = Duration.ofHours(1);

    private DevJwtMinter() {}

    public static void main(String[] args) throws JOSEException {
        String secret = System.getenv("BLUEPRINTS_DEV_JWT_SECRET");
        String scopes = args.length == 0 ? "models.use" : String.join(" ", args);
        System.out.println(mint(secret, scopes, Instant.now()));
    }

    /** Mints a token with subject {@code dev-user}, the given space-separated scopes and a one-hour expiry. */
    public static String mint(String secret, String scopes, Instant now) throws JOSEException {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Set BLUEPRINTS_DEV_JWT_SECRET (the same value the app was started with)");
        }
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalArgumentException("BLUEPRINTS_DEV_JWT_SECRET must be at least 32 bytes");
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("dev-user").issuer("dev-jwt-minter").claim("scope", scopes)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plus(LIFETIME))).build());
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
}
