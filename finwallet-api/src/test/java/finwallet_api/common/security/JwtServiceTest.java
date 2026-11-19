package finwallet_api.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-key-for-finwallet-jwt-verification-0123456789abcdef";
    private static final long EXPIRATION_SECONDS = 900;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_SECONDS);
    }

    @Test
    void generatesValidTokenAndExtractsClaims() {
        String email = "trader@finwallet.test";
        Long userId = 42L;

        String token = jwtService.generateToken(email, userId);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals(email, jwtService.extractEmail(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(EXPIRATION_SECONDS, jwtService.getExpirationSeconds());
    }

    @Test
    void rejectsExpiredToken() {
        String email = "expired@finwallet.test";
        // Negative expiration -> expired token
        String token = jwtService.generateToken(email, 1L, -1000L);

        assertFalse(jwtService.validateToken(token));
        assertThrows(ExpiredJwtException.class, () -> jwtService.extractEmail(token));
    }

    @Test
    void rejectsTamperedTokenSignature() {
        String token = jwtService.generateToken("alice@finwallet.test", 2L);

        // Tamper with the signature (last part of JWT)
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "invalidSignature";

        assertFalse(jwtService.validateToken(tamperedToken));
        assertThrows(SignatureException.class, () -> jwtService.extractEmail(tamperedToken));
    }

    @Test
    void rejectsMalformedToken() {
        String malformedToken = "not.a.valid.jwt.token";

        assertFalse(jwtService.validateToken(malformedToken));
        assertThrows(MalformedJwtException.class, () -> jwtService.extractEmail(malformedToken));
    }

    @Test
    void rejectsUnsignedTokensAndSignedTokensWithoutExpiration() {
        assertFalse(jwtService.validateToken(Jwts.builder().subject("alice@finwallet.test")
                .expiration(Date.from(Instant.now().plusSeconds(60))).compact()));
        assertFalse(jwtService.validateToken(Jwts.builder().subject("alice@finwallet.test")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact()));
    }

    @Test
    void rejectsAnotherAlgorithmEvenWhenItUsesTheSameSecret() {
        var alternateKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token = Jwts.builder().subject("alice@finwallet.test")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(alternateKey, Jwts.SIG.HS256).compact();
        assertFalse(jwtService.validateToken(token));
    }

    @Test
    void acceptsPreviouslyIssuedSignatureAlgorithmForTheConfiguredKey() {
        String legacy = Jwts.builder().subject("alice@finwallet.test").claim("userId", 7L)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertTrue(jwtService.validateToken(legacy));
        assertEquals(7L, jwtService.extractAllClaims(legacy).get("userId", Long.class));
    }

}
