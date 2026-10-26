package ru.backend.session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.ttl-minutes}") long ttlMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET должен быть не короче 32 байт");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issue(AuthUser u) {
        Instant now = Instant.now();
        var b = Jwts.builder()
                .subject(u.id().toString())
                .claim("role", u.role().name())
                .claim("login", u.login())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (u.companyId() != null) b.claim("companyId", u.companyId().toString());
        return b.signWith(key).compact();
    }

    public AuthUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String company = c.get("companyId", String.class);
        return new AuthUser(UUID.fromString(c.getSubject()), Role.valueOf(c.get("role", String.class)),
                company == null ? null : UUID.fromString(company), c.get("login", String.class));
    }
}
