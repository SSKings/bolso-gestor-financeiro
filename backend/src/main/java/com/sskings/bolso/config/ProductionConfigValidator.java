package com.sskings.bolso.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perfil prod: a aplicação NÃO sobe com configuração insegura — lista todos os problemas de uma vez.
 * <ul>
 *   <li>JWT_SECRET com 32+ caracteres, variado, e não de exemplo/teste (quem tem o segredo forja qualquer login).</li>
 *   <li>CORS_ALLOWED_ORIGINS obrigatório, só https e sem curinga.</li>
 *   <li>DATABASE_URL de PostgreSQL.</li>
 *   <li>A API conecta com um usuário só de DML, diferente do dono das tabelas (que roda as migrações) e do
 *       superusuário: uma falha de SQL não vira controle do banco (lição da revisão 5 do ShopEasy).</li>
 * </ul>
 */
@Component
@Profile("prod")
public class ProductionConfigValidator {

    public ProductionConfigValidator(BolsoProperties props, @Value("${spring.datasource.url:}") String databaseUrl,
                                     @Value("${spring.datasource.username:}") String appUser,
                                     @Value("${spring.flyway.user:}") String migrationUser) {
        List<String> problems = new ArrayList<>(problems(props.jwt().secret(), props.cors().allowedOrigins(), databaseUrl));
        problems.addAll(databaseUserProblems(appUser, migrationUser));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuração de produção insegura:\n - " + String.join("\n - ", problems));
        }
    }

    static List<String> problems(String jwtSecret, List<String> origins, String databaseUrl) {
        List<String> p = new ArrayList<>();
        String secret = jwtSecret == null ? "" : jwtSecret;
        String lower = secret.toLowerCase(Locale.ROOT);
        if (secret.length() < 32) {
            p.add("JWT_SECRET: mínimo de 32 caracteres (gere com: openssl rand -base64 48)");
        } else if (secret.chars().distinct().count() < 10 || lower.contains("change") || lower.contains("test")
                || lower.contains("secret") || lower.contains("example")) {
            p.add("JWT_SECRET: parece de exemplo ou de teste; gere um aleatório (openssl rand -base64 48)");
        }
        if (origins == null || origins.isEmpty()) {
            p.add("CORS_ALLOWED_ORIGINS: informe a origem do front-end (ex.: https://bolso.seudominio.com.br)");
        } else {
            for (String o : origins) {
                if (o.contains("*") || !o.startsWith("https://")) {
                    p.add("CORS_ALLOWED_ORIGINS: '" + o + "' — só origens https exatas, sem curinga");
                }
            }
        }
        if (databaseUrl == null || !databaseUrl.startsWith("jdbc:postgresql:")) {
            p.add("DATABASE_URL: informe o PostgreSQL de produção (jdbc:postgresql://...)");
        }
        return p;
    }

    static List<String> databaseUserProblems(String appUser, String migrationUser) {
        List<String> p = new ArrayList<>();
        if (migrationUser == null || migrationUser.isBlank()) {
            p.add("DATABASE_MIGRATION_USERNAME: informe o dono das tabelas (o Flyway migra com ele)");
        }
        if (appUser == null || appUser.isBlank() || appUser.equals(migrationUser) || appUser.equals("postgres")) {
            p.add("DATABASE_USERNAME: a API usa um usuário só de dados, diferente do dono das tabelas e do superusuário");
        }
        return p;
    }
}
