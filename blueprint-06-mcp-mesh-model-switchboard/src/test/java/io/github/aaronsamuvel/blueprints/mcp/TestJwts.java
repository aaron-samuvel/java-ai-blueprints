package io.github.aaronsamuvel.blueprints.mcp;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Mints HS256 JWTs for tests. The signing secret is random per JVM run and never stored anywhere,
 * so there is no external authorization server and no committed credential.
 */
public final class TestJwts {

    public static final String SECRET;

    static {
        byte[] b = new byte[48];
        new SecureRandom().nextBytes(b);
        SECRET = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private TestJwts() {}

    public static String token(String scopes) {
        return token(scopes, SECRET, Instant.now().plusSeconds(300));
    }

    public static String token(String scopes, String secret, Instant expiry) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                    .subject("test-user").issuer("test").claim("scope", scopes).issueTime(new Date()).expirationTime(Date.from(expiry)).build());
            jwt.sign(new MACSigner(secret.getBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
