package com.sskings.bolso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Tudo que é configurável do Bolso (prefixo "bolso" no application.yml). */
@ConfigurationProperties(prefix = "bolso")
public record BolsoProperties(Jwt jwt, Login login, RateLimit rateLimit, long maxRequestBytes, Cors cors, Invite invite,
                             java.time.ZoneId zone) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Login(int maxFailures, int lockMinutes) {}

    public record RateLimit(int authPerMinute) {}

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Invite(int ttlHours) {}
}
