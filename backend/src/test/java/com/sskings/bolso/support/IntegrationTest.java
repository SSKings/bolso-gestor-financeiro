package com.sskings.bolso.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base dos testes de integração: a aplicação inteira (filtros de segurança, validação, Flyway, PostgreSQL).
 * Cada requisição sai de um IP diferente, a menos que o teste fixe um: o limite por IP não interfere nos outros
 * testes, e o teste que mede o limite controla a origem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String PASSWORD = "Vagalume4821";
    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected MutableClock clock;

    /** Bot do Telegram ligado contra a API de mentira: o transporte (polling, envio) roda de verdade nos testes. */
    @org.springframework.test.context.DynamicPropertySource
    static void telegram(org.springframework.test.context.DynamicPropertyRegistry r) {
        r.add("bolso.telegram.enabled", () -> "true");
        r.add("bolso.telegram.token", () -> FakeTelegram.TOKEN);
        r.add("bolso.telegram.bot-username", () -> "BolsoTesteBot");
        r.add("bolso.telegram.api-url", () -> FakeTelegram.get().url());
        r.add("bolso.telegram.poll-timeout-seconds", () -> "1");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected static String ip() {
        int n = IPS.incrementAndGet();
        return "10." + (n >> 16 & 255) + "." + (n >> 8 & 255) + "." + (n & 255);
    }

    protected static String email() {
        return "u-" + UUID.randomUUID().toString().substring(0, 12) + "@teste.com";
    }

    /** Requisição completa: origem, token (opcional) e corpo JSON (opcional, objeto ou String crua). */
    protected MockHttpServletResponse call(MockHttpServletRequestBuilder req, String token, Object body, String ip) throws Exception {
        req.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON)
                    .content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    protected MockHttpServletResponse call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        return call(req, token, body, ip());
    }

    protected JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    /** Usuário recém-cadastrado: token, id, e o espaço pessoal criado no cadastro. */
    public record Session(String token, String userId, String email, String workspaceId) {}

    protected Session register() throws Exception {
        return register("Ana Souza", email());
    }

    protected Session register(String name, String email) throws Exception {
        MockHttpServletResponse r = call(post("/api/auth/register"), null,
                Map.of("name", name, "email", email, "password", PASSWORD));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        JsonNode b = body(r);
        String token = b.get("token").asText();
        JsonNode me = body(call(get("/api/me"), token, null));
        return new Session(token, me.get("id").asText(), email, me.get("workspaces").get(0).get("id").asText());
    }

    protected String ws(Session s, String path) {
        return "/api/workspaces/" + s.workspaceId() + path;
    }
}
