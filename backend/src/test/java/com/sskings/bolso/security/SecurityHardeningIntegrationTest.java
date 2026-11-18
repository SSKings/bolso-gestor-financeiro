package com.sskings.bolso.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class SecurityHardeningIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("Respostas da API: CSP sem nada, nosniff, sem frame, sem referrer e sem cache")
    void securityHeaders() throws Exception {
        Session s = register();
        MockHttpServletResponse r = call(get("/api/me"), s.token(), null);
        assertThat(r.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; frame-ancestors 'none'");
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    @DisplayName("CORS: só a origem configurada; outra origem não recebe permissão")
    void cors() throws Exception {
        MockHttpServletResponse ok = call(options("/api/me").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"), null, null);
        assertThat(ok.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5173");
        assertThat(ok.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();

        MockHttpServletResponse evil = call(options("/api/me").header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"), null, null);
        assertThat(evil.getStatus()).isEqualTo(403);
        assertThat(evil.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    @DisplayName("Corpo maior que 64 KB → 413, com ou sem Content-Length")
    void requestSizeLimit() throws Exception {
        String big = "{\"name\":\"" + "a".repeat(70_000) + "\",\"email\":\"a@b.com\",\"password\":\"x\"}";
        assertThat(call(post("/api/auth/register"), null, big).getStatus()).isEqualTo(413);
    }

    @Test
    @DisplayName("Tokens forjados: alg none, outro segredo, outro emissor, sem versão, usuário inexistente → 401")
    void forgedTokens() throws Exception {
        Session s = register();
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"bolso-api\",\"sub\":\"" + s.userId() + "\",\"ver\":0}").getBytes(StandardCharsets.UTF_8));
        String none = b64 + "." + payload + ".";
        String otherSecret = JWT.create().withIssuer("bolso-api").withSubject(s.userId()).withClaim("ver", 0)
                .withExpiresAt(Instant.now().plusSeconds(600)).sign(Algorithm.HMAC256("outro-segredo-qualquer-0123456789-abcdefghij"));
        Algorithm real = Algorithm.HMAC256("test-secret-only-for-tests-0123456789-abcdefghijklmnopqrstuvwxyz");
        String otherIssuer = JWT.create().withIssuer("shopeasy").withSubject(s.userId()).withClaim("ver", 0)
                .withExpiresAt(Instant.now().plusSeconds(600)).sign(real);
        String noVersion = JWT.create().withIssuer("bolso-api").withSubject(s.userId())
                .withExpiresAt(Instant.now().plusSeconds(600)).sign(real);
        String ghost = JWT.create().withIssuer("bolso-api").withSubject("00000000-0000-0000-0000-000000000000").withClaim("ver", 0)
                .withExpiresAt(Instant.now().plusSeconds(600)).sign(real);
        String notUuid = JWT.create().withIssuer("bolso-api").withSubject("admin").withClaim("ver", 0)
                .withExpiresAt(Instant.now().plusSeconds(600)).sign(real);
        for (String t : new String[]{none, otherSecret, otherIssuer, noVersion, ghost, notUuid, "abc", ""}) {
            assertThat(call(get("/api/me"), t, null).getStatus()).as(t).isEqualTo(401);
        }
        assertThat(call(get("/api/me").header("Authorization", "Basic YTpi"), null, null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Actuator: só o health, sem detalhes; o resto não existe para quem está de fora")
    void actuator() throws Exception {
        MockHttpServletResponse health = call(get("/actuator/health"), null, null);
        assertThat(health.getStatus()).isEqualTo(200);
        assertThat(health.getContentAsString()).isEqualTo("{\"status\":\"UP\"}");
        for (String p : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator"}) {
            assertThat(call(get(p), null, null).getStatus()).as(p).isIn(401, 404);
        }
        Session s = register();
        for (String p : new String[]{"/actuator/env", "/actuator/heapdump"}) {
            assertThat(call(get(p), s.token(), null).getStatus()).as(p + " logado").isEqualTo(404);
        }
    }

    @Test
    @DisplayName("Documentação da API (Swagger) desligada fora do perfil dev")
    void docsDisabledOutsideDev() throws Exception {
        assertThat(call(get("/v3/api-docs"), null, null).getStatus()).isIn(401, 404);
        Session s = register();
        assertThat(call(get("/v3/api-docs"), s.token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Erros não vazam detalhe interno: rota inexistente, método, JSON quebrado, tipo de mídia")
    void errorsDoNotLeak() throws Exception {
        Session s = register();
        MockHttpServletResponse missing = call(get("/api/nao-existe"), s.token(), null);
        assertThat(missing.getStatus()).isEqualTo(404);
        MockHttpServletResponse method = call(patch("/api/me"), s.token(), null);
        assertThat(method.getStatus()).isEqualTo(405);
        MockHttpServletResponse broken = call(post(ws(s, "/accounts")), s.token(), "{\"name\": ");
        assertThat(broken.getStatus()).isEqualTo(400);
        assertThat(broken.getContentAsString()).isEqualTo("{\"status\":400,\"message\":\"Malformed JSON request\",\"errors\":null}");
        MockHttpServletResponse media = call(post(ws(s, "/accounts")).contentType("text/plain").content("x"), s.token(), null);
        assertThat(media.getStatus()).isEqualTo(415);
        for (MockHttpServletResponse r : new MockHttpServletResponse[]{missing, method, broken, media}) {
            assertThat(r.getContentAsString()).doesNotContain("Exception", "org.", "SQL", "at com.");
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext context;

    @Test
    @DisplayName("Sem o usuário padrão do Spring (\"user\" com senha gerada no log): o login é só o do Bolso")
    void noDefaultUser() {
        assertThat(context.getBeanNamesForType(org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
    }

    @Test
    @DisplayName("Rotas exigem login (exceto cadastro, login e health)")
    void authenticationRequired() throws Exception {
        for (String p : new String[]{"/api/me", "/api/workspaces/00000000-0000-0000-0000-000000000000/accounts"}) {
            MockHttpServletResponse r = call(get(p), null, null);
            assertThat(r.getStatus()).as(p).isEqualTo(401);
            assertThat(r.getContentAsString()).contains("Authentication required");
        }
        assertThat(call(post("/api/invites/" + "a".repeat(43) + "/accept"), null, null).getStatus()).isEqualTo(401);
        assertThat(call(post("/api/workspaces"), null, Map.of("name", "X")).getStatus()).isEqualTo(401);
    }
}
