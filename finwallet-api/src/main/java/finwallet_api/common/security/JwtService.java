package finwallet_api.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final MacAlgorithm signingAlgorithm;
    private final JwtParser parser;
    private final long expirationSeconds;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:900}") long expirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // Pin verification to the same algorithm the previous signer selected for this key length.
        this.signingAlgorithm = switch (signingKey.getAlgorithm()) {
            case "HmacSHA512" -> Jwts.SIG.HS512;
            case "HmacSHA384" -> Jwts.SIG.HS384;
            case "HmacSHA256" -> Jwts.SIG.HS256;
            default -> throw new IllegalArgumentException("Unsupported JWT signing key algorithm");
        };
        var algorithms = Jwts.parser().verifyWith(signingKey).sig();
        for (var candidate : Jwts.SIG.get().values()) {
            if (!candidate.getId().equals(signingAlgorithm.getId())) {
                algorithms.remove(candidate);
            }
        }
        this.parser = algorithms.and().build();
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String email, Long userId) {
        return generateToken(email, userId, expirationSeconds * 1000L);
    }

    public String generateToken(String email, Long userId, long expirationMillis) {
        long nowMillis = System.currentTimeMillis();
        Date issuedAt = new Date(nowMillis);
        Date expiration = new Date(nowMillis + expirationMillis);

        var builder = Jwts.builder()
                .subject(email)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey, signingAlgorithm);

        if (userId != null) {
            builder.claim("userId", userId);
        }

        return builder.compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Token must include a subject and expiration");
        }
        return claims;
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
