package com.sskings.bolso.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {

    private static final String GOOD_SECRET = "Zx9!k2Lq#v8Rt4Wm6Ny1Pb3Hc7Jd5Fg0Se";

    @Test
    @DisplayName("Configuração de produção correta passa")
    void validPasses() {
        assertThat(ProductionConfigValidator.problems(GOOD_SECRET, List.of("https://bolso.app.br"), "jdbc:postgresql://db:5432/bolso")).isEmpty();
    }

    @Test
    @DisplayName("Segredo JWT ausente, curto ou de exemplo é recusado")
    void jwtSecret() {
        for (String s : new String[]{null, "", "curto", "a".repeat(40), "change-me-change-me-change-me-change-me",
                "my-super-secret-key-for-jwt-signing-0123456789", "example-key-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789",
                "test-secret-only-for-tests-0123456789-abcdefghijklmnopqrstuvwxyz"}) {
            assertThat(ProductionConfigValidator.problems(s, List.of("https://bolso.app.br"), "jdbc:postgresql://db/bolso"))
                    .as(String.valueOf(s)).anyMatch(p -> p.startsWith("JWT_SECRET"));
        }
    }

    @Test
    @DisplayName("CORS: obrigatório, só https, nunca curinga")
    void cors() {
        assertThat(ProductionConfigValidator.problems(GOOD_SECRET, null, "jdbc:postgresql://db/bolso"))
                .anyMatch(p -> p.startsWith("CORS_ALLOWED_ORIGINS"));
        for (List<String> origins : List.of(List.<String>of(), List.of("*"), List.of("http://bolso.app.br"),
                List.of("https://bolso.app.br", "https://*.app.br"), List.of("http://localhost:5173"))) {
            assertThat(ProductionConfigValidator.problems(GOOD_SECRET, origins, "jdbc:postgresql://db/bolso"))
                    .as(origins.toString()).anyMatch(p -> p.startsWith("CORS_ALLOWED_ORIGINS"));
        }
    }

    @Test
    @DisplayName("Banco: obrigatório e PostgreSQL")
    void database() {
        for (String url : new String[]{null, "", "jdbc:h2:mem:x"}) {
            assertThat(ProductionConfigValidator.problems(GOOD_SECRET, List.of("https://bolso.app.br"), url))
                    .as(String.valueOf(url)).anyMatch(p -> p.startsWith("DATABASE_URL"));
        }
    }

    @Test
    @DisplayName("Usuário do banco da API: diferente do dono das tabelas (migrações) e do superusuário")
    void databaseUsers() {
        assertThat(ProductionConfigValidator.databaseUserProblems("bolso_app", "bolso_owner")).isEmpty();
        assertThat(ProductionConfigValidator.databaseUserProblems("bolso_owner", "bolso_owner")).anyMatch(p -> p.startsWith("DATABASE_USERNAME"));
        assertThat(ProductionConfigValidator.databaseUserProblems("postgres", "bolso_owner")).anyMatch(p -> p.startsWith("DATABASE_USERNAME"));
        assertThat(ProductionConfigValidator.databaseUserProblems("bolso_app", "")).anyMatch(p -> p.startsWith("DATABASE_MIGRATION_USERNAME"));
        assertThat(ProductionConfigValidator.databaseUserProblems("", "bolso_owner")).anyMatch(p -> p.startsWith("DATABASE_USERNAME"));
    }

    @Test
    @DisplayName("Com problema, a aplicação não sobe e a mensagem lista todos")
    void failsFast() {
        assertThatThrownBy(() -> new ProductionConfigValidator(
                new BolsoProperties(new BolsoProperties.Jwt("x", 60), null, null, 1, new BolsoProperties.Cors(List.of()), null, null),
                "", "postgres", "postgres"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET").hasMessageContaining("CORS_ALLOWED_ORIGINS").hasMessageContaining("DATABASE_URL")
                .hasMessageContaining("DATABASE_USERNAME");
    }
}
