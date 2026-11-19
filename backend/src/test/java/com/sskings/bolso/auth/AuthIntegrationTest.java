package com.sskings.bolso.auth;

import tools.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuthIntegrationTest extends IntegrationTest {

    private MockHttpServletResponse register(Object body) throws Exception {
        return call(post("/api/auth/register"), null, body);
    }

    private MockHttpServletResponse login(String email, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", email, "password", password));
    }

    @Test
    @DisplayName("Cadastro: devolve token e cria o espaço pessoal (dono), com conta e categorias padrão")
    void registerCreatesPersonalWorkspace() throws Exception {
        String email = email();
        MockHttpServletResponse r = register(Map.of("name", "Ana Souza", "email", email, "password", PASSWORD));
        assertThat(r.getStatus()).isEqualTo(201);
        JsonNode b = body(r);
        assertThat(b.get("token").asString()).isNotBlank();
        assertThat(b.get("user").get("email").asString()).isEqualTo(email);
        assertThat(b.get("user").has("passwordHash")).isFalse();

        JsonNode me = body(call(get("/api/me"), b.get("token").asString(), null));
        assertThat(me.get("name").asString()).isEqualTo("Ana Souza");
        assertThat(me.get("workspaces")).hasSize(1);
        assertThat(me.get("workspaces").get(0).get("name").asString()).isEqualTo("Pessoal");
        assertThat(me.get("workspaces").get(0).get("role").asString()).isEqualTo("OWNER");

        String wid = me.get("workspaces").get(0).get("id").asString();
        String token = b.get("token").asString();
        assertThat(body(call(get("/api/workspaces/" + wid + "/accounts"), token, null))).hasSize(1);
        JsonNode cats = body(call(get("/api/workspaces/" + wid + "/categories"), token, null));
        assertThat(cats.size()).isGreaterThanOrEqualTo(8);
        assertThat(cats.findValuesAsString("kind")).contains("INCOME", "EXPENSE");
    }

    @Test
    @DisplayName("E-mail é normalizado (espaços e maiúsculas) e não pode repetir")
    void emailNormalizedAndUnique() throws Exception {
        String email = email();
        assertThat(register(Map.of("name", "Ana", "email", "  " + email.toUpperCase() + " ", "password", PASSWORD)).getStatus()).isEqualTo(201);
        MockHttpServletResponse dup = register(Map.of("name", "Outra", "email", email, "password", PASSWORD));
        assertThat(dup.getStatus()).isEqualTo(409);
        assertThat(body(dup).get("message").asString()).isEqualTo("Email already in use");
        assertThat(login(email, PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Validação do cadastro: campos obrigatórios, e-mail, tamanho e senha fraca")
    void registerValidation() throws Exception {
        List<Map<String, Object>> invalid = List.of(
                Map.of("email", email(), "password", PASSWORD),                                  // sem nome
                Map.of("name", "Ana", "password", PASSWORD),                                      // sem e-mail
                Map.of("name", "Ana", "email", "nao-e-email", "password", PASSWORD),
                Map.of("name", "Ana", "email", email(), "password", "curta1"),                    // < 8
                Map.of("name", "Ana", "email", email(), "password", "somenteletras"),             // sem número
                Map.of("name", "Ana", "email", email(), "password", "a1".repeat(40)),             // > 72 (limite do BCrypt)
                Map.of("name", "Ana", "email", email(), "password", "senha123"),                  // comum
                Map.of("name", "Ana", "email", email(), "password", "Bolso2026!"),                // nome do app
                Map.of("name", "Ana Beatriz", "email", email(), "password", "beatriz1990x"),      // nome da pessoa
                Map.of("name", "x".repeat(101), "email", email(), "password", PASSWORD));
        for (Map<String, Object> body : invalid) {
            MockHttpServletResponse r = register(body);
            assertThat(r.getStatus()).as(body.toString()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("Campo a mais no JSON (ex.: role, tokenVersion) é recusado, não ignorado")
    void unknownFieldsRejected() throws Exception {
        MockHttpServletResponse r = register(Map.of("name", "Ana", "email", email(), "password", PASSWORD, "role", "ADMIN"));
        assertThat(r.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Login: mesma resposta para senha errada e e-mail inexistente (não revela quem tem conta)")
    void loginDoesNotRevealAccounts() throws Exception {
        Session s = register();
        MockHttpServletResponse wrong = login(s.email(), "Errada-0000x");
        MockHttpServletResponse unknown = login("ninguem-" + s.email(), "Errada-0000x");
        assertThat(wrong.getStatus()).isEqualTo(401);
        assertThat(unknown.getStatus()).isEqualTo(401);
        assertThat(body(wrong).get("message").asString()).isEqualTo(body(unknown).get("message").asString())
                .isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Bloqueio: 5 falhas seguidas bloqueiam a conta por 15 min, mesmo com a senha certa; depois volta")
    void lockoutAfterFailures() throws Exception {
        Session s = register();
        for (int i = 0; i < 5; i++) assertThat(login(s.email(), "Errada-0000x").getStatus()).isEqualTo(401);
        MockHttpServletResponse locked = login(s.email(), PASSWORD);
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(body(locked).get("message").asString()).isEqualTo("Too many failed attempts. Try again later");

        clock.advance(Duration.ofMinutes(16));
        assertThat(login(s.email(), PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Bloqueio progressivo: o segundo bloqueio seguido dura o dobro; login certo zera a contagem")
    void lockoutIsProgressive() throws Exception {
        Session s = register();
        for (int i = 0; i < 5; i++) login(s.email(), "Errada-0000x");
        clock.advance(Duration.ofMinutes(16));
        for (int i = 0; i < 5; i++) login(s.email(), "Errada-0000x");
        clock.advance(Duration.ofMinutes(16));
        assertThat(login(s.email(), PASSWORD).getStatus()).as("segundo bloqueio: 30 min").isEqualTo(429);
        clock.advance(Duration.ofMinutes(15));
        assertThat(login(s.email(), PASSWORD).getStatus()).isEqualTo(200);

        for (int i = 0; i < 4; i++) login(s.email(), "Errada-0000x");
        assertThat(login(s.email(), PASSWORD).getStatus()).as("o sucesso zerou as falhas").isEqualTo(200);
        for (int i = 0; i < 4; i++) login(s.email(), "Errada-0000x");
        assertThat(login(s.email(), PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Logout revoga o token; sem token, token adulterado ou expirado → 401")
    void tokenLifecycle() throws Exception {
        Session s = register();
        assertThat(call(get("/api/me"), s.token(), null).getStatus()).isEqualTo(200);
        assertThat(call(get("/api/me"), null, null).getStatus()).isEqualTo(401);
        String tampered = s.token().substring(0, s.token().length() - 3) + "abc";
        assertThat(call(get("/api/me"), tampered, null).getStatus()).isEqualTo(401);

        String other = body(login(s.email(), PASSWORD)).get("token").asString();
        assertThat(call(post("/api/auth/logout"), s.token(), null).getStatus()).isEqualTo(204);
        assertThat(call(get("/api/me"), s.token(), null).getStatus()).isEqualTo(401);
        assertThat(call(get("/api/me"), other, null).getStatus()).as("logout encerra todas as sessões").isEqualTo(401);

        String fresh = body(login(s.email(), PASSWORD)).get("token").asString();
        clock.advance(Duration.ofHours(13));
        assertThat(call(get("/api/me"), fresh, null).getStatus()).as("expira em 12 h").isEqualTo(401);
    }

    @Test
    @DisplayName("Troca de senha: exige a atual, aplica a política e derruba os tokens antigos")
    void changePassword() throws Exception {
        Session s = register();
        assertThat(call(put("/api/me/password"), s.token(),
                Map.of("currentPassword", "Errada-0000x", "newPassword", "Pirilampo7390")).getStatus()).isEqualTo(400);
        assertThat(call(put("/api/me/password"), s.token(),
                Map.of("currentPassword", PASSWORD, "newPassword", "senha123")).getStatus()).isEqualTo(400);

        MockHttpServletResponse ok = call(put("/api/me/password"), s.token(),
                Map.of("currentPassword", PASSWORD, "newPassword", "Pirilampo7390"));
        assertThat(ok.getStatus()).isEqualTo(200);
        String newToken = body(ok).get("token").asString();
        assertThat(call(get("/api/me"), s.token(), null).getStatus()).isEqualTo(401);
        assertThat(call(get("/api/me"), newToken, null).getStatus()).isEqualTo(200);
        assertThat(login(s.email(), PASSWORD).getStatus()).isEqualTo(401);
        assertThat(login(s.email(), "Pirilampo7390").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Limite por IP nas rotas de autenticação: 20/min; outro IP não é afetado")
    void authRateLimitPerIp() throws Exception {
        String attacker = ip();
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            codes.add(call(post("/api/auth/login"), null, Map.of("email", email(), "password", "Errada-0000x"), attacker).getStatus());
        }
        assertThat(codes.subList(0, 20)).containsOnly(401);
        assertThat(codes.subList(20, 22)).containsOnly(429);
        assertThat(login(email(), "Errada-0000x").getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Nova senha igual à atual é recusada")
    void newPasswordMustDiffer() throws Exception {
        Session s = register();
        MockHttpServletResponse r = call(put("/api/me/password"), s.token(), Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD));
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r).get("message").asString()).isEqualTo("New password must be different from the current one");
    }
}
