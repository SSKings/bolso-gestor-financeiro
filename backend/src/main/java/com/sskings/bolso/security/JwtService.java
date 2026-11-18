package com.sskings.bolso.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.sskings.bolso.config.BolsoProperties;
import com.sskings.bolso.user.User;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JWT HS256: sujeito = id do usuário (não o e-mail), issuer fixo, expiração e a versão de token ("ver"). Logout e
 * troca de senha incrementam User.tokenVersion: todos os tokens anteriores param de valer na hora.
 */
@Service
public class JwtService {

    static final String ISSUER = "bolso-api";
    static final String VERSION_CLAIM = "ver";

    private final Algorithm algorithm;
    private final long expirationMinutes;
    private final Clock clock;

    public JwtService(BolsoProperties props, Clock clock) {
        this.algorithm = Algorithm.HMAC256(props.jwt().secret());
        this.expirationMinutes = props.jwt().expirationMinutes();
        this.clock = clock;
    }

    public String generate(User user) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withClaim(VERSION_CLAIM, user.getTokenVersion())
                .withIssuedAt(clock.instant())
                .withExpiresAt(clock.instant().plus(expirationMinutes, ChronoUnit.MINUTES))
                .sign(algorithm);
    }

    /** Válido → (id, versão); inválido, adulterado, de outro emissor ou expirado (no relógio da aplicação) → null. */
    public Claims validate(String token) {
        try {
            var verification = (JWTVerifier.BaseVerification) JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .withClaimPresence(VERSION_CLAIM);
            DecodedJWT jwt = verification.build(clock).verify(token);
            return new Claims(UUID.fromString(jwt.getSubject()), jwt.getClaim(VERSION_CLAIM).asInt());
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    public record Claims(UUID userId, int version) {}
}
