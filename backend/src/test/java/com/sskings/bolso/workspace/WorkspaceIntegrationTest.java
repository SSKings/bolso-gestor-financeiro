package com.sskings.bolso.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class WorkspaceIntegrationTest extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;

    private String invite(Session owner) throws Exception {
        MockHttpServletResponse r = call(post(ws(owner, "/invites")), owner.token(), null);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return body(r).get("code").asText();
    }

    private MockHttpServletResponse accept(Session who, String code) throws Exception {
        return call(post("/api/invites/" + code + "/accept"), who.token(), null);
    }

    private Session withMember(Session owner) throws Exception {
        Session member = register("Bruno Lima", email());
        assertThat(accept(member, invite(owner)).getStatus()).isEqualTo(200);
        return new Session(member.token(), member.userId(), member.email(), owner.workspaceId());
    }

    @Test
    @DisplayName("Criar outro espaço: o criador é dono e ele já vem com conta e categorias")
    void createWorkspace() throws Exception {
        Session ana = register();
        MockHttpServletResponse r = call(post("/api/workspaces"), ana.token(), Map.of("name", "Casa"));
        assertThat(r.getStatus()).isEqualTo(201);
        String id = body(r).get("id").asText();
        JsonNode me = body(call(get("/api/me"), ana.token(), null));
        assertThat(me.get("workspaces").findValuesAsText("name")).containsExactly("Pessoal", "Casa");
        assertThat(body(call(get("/api/workspaces/" + id + "/accounts"), ana.token(), null))).hasSize(1);
        assertThat(call(post("/api/workspaces"), ana.token(), Map.of("name", " ")).getStatus()).isEqualTo(400);
        assertThat(call(post("/api/workspaces"), ana.token(), Map.of("name", "Casa\nFalsa")).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Convite: código de uso único; quem aceita vira membro e passa a ver o espaço")
    void inviteFlow() throws Exception {
        Session ana = register();
        String code = invite(ana);
        assertThat(code).matches("[A-Za-z0-9_-]{43}");

        Session bruno = register("Bruno Lima", email());
        MockHttpServletResponse ok = accept(bruno, code);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(body(ok).get("workspaceId").asText()).isEqualTo(ana.workspaceId());

        JsonNode me = body(call(get("/api/me"), bruno.token(), null));
        assertThat(me.get("workspaces")).hasSize(2);
        assertThat(me.get("workspaces").get(1).get("role").asText()).isEqualTo("MEMBER");
        assertThat(call(get(ws(ana, "/accounts")), bruno.token(), null).getStatus()).isEqualTo(200);

        Session carla = register("Carla Dias", email());
        assertThat(accept(carla, code).getStatus()).as("uso único").isEqualTo(404);
        assertThat(body(accept(carla, code)).get("message").asText()).isEqualTo("Invalid or expired invite");
    }

    @Test
    @DisplayName("Convite: só o hash fica no banco; vence em 72 h; quem já é membro não aceita de novo")
    void inviteSecurity() throws Exception {
        Session ana = register();
        String code = invite(ana);
        String stored = jdbc.queryForObject("select token_hash from invites where workspace_id = ?::uuid order by created_at desc limit 1",
                String.class, ana.workspaceId());
        assertThat(stored).hasSize(64).doesNotContain(code);

        Session bruno = register("Bruno Lima", email());
        clock.advance(Duration.ofHours(73));
        String fresh = body(call(post("/api/auth/login"), null, Map.of("email", bruno.email(), "password", PASSWORD))).get("token").asText();
        assertThat(call(post("/api/invites/" + code + "/accept"), fresh, null).getStatus()).as("vencido").isEqualTo(404);
        clock.reset();

        assertThat(accept(ana, invite(ana)).getStatus()).as("o dono já é membro").isEqualTo(409);
        assertThat(accept(bruno, "x".repeat(43)).getStatus()).isEqualTo(404);
        assertThat(accept(bruno, "curto").getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Convites pendentes: o dono lista e revoga; código revogado não funciona")
    void listAndRevokeInvites() throws Exception {
        Session ana = register();
        String code = invite(ana);
        JsonNode pending = body(call(get(ws(ana, "/invites")), ana.token(), null));
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).has("code")).as("o código não é exibido de novo").isFalse();
        String inviteId = pending.get(0).get("id").asText();
        assertThat(call(delete(ws(ana, "/invites/" + inviteId)), ana.token(), null).getStatus()).isEqualTo(204);
        assertThat(accept(register(), code).getStatus()).isEqualTo(404);
        assertThat(body(call(get(ws(ana, "/invites")), ana.token(), null))).isEmpty();
    }

    @Test
    @DisplayName("Membro usa o espaço, mas não administra: convidar, renomear, remover e excluir → 403")
    void memberCannotAdminister() throws Exception {
        Session ana = register();
        Session bruno = withMember(ana);
        assertThat(call(post(ws(bruno, "/invites")), bruno.token(), null).getStatus()).isEqualTo(403);
        assertThat(call(get(ws(bruno, "/invites")), bruno.token(), null).getStatus()).isEqualTo(403);
        assertThat(call(put(ws(bruno, "")), bruno.token(), Map.of("name", "Meu")).getStatus()).isEqualTo(403);
        assertThat(call(delete(ws(bruno, "/members/" + ana.userId())), bruno.token(), null).getStatus()).isEqualTo(403);
        assertThat(call(put(ws(bruno, "/members/" + bruno.userId())), bruno.token(), Map.of("role", "OWNER")).getStatus()).isEqualTo(403);
        assertThat(call(delete(ws(bruno, "")), bruno.token(), null).getStatus()).isEqualTo(403);

        assertThat(call(put(ws(ana, "")), ana.token(), Map.of("name", "Casa")).getStatus()).isEqualTo(200);
        assertThat(body(call(get("/api/me"), bruno.token(), null)).get("workspaces").get(1).get("name").asText()).isEqualTo("Casa");
    }

    @Test
    @DisplayName("Membros: lista com papel; dono remove e o removido perde o acesso na hora")
    void membersAndRemoval() throws Exception {
        Session ana = register();
        Session bruno = withMember(ana);
        JsonNode members = body(call(get(ws(ana, "/members")), bruno.token(), null));
        assertThat(members.findValuesAsText("role")).containsExactly("OWNER", "MEMBER");
        assertThat(members.findValuesAsText("name")).containsExactly("Ana Souza", "Bruno Lima");

        assertThat(call(delete(ws(ana, "/members/" + bruno.userId())), ana.token(), null).getStatus()).isEqualTo(204);
        assertThat(call(get(ws(ana, "/accounts")), bruno.token(), null).getStatus()).isEqualTo(404);
        assertThat(body(call(get("/api/me"), bruno.token(), null)).get("workspaces")).hasSize(1);
    }

    @Test
    @DisplayName("Sair: membro sai; o último dono não sai (o espaço ficaria sem dono) nem se remove")
    void leaveRules() throws Exception {
        Session ana = register();
        Session bruno = withMember(ana);
        MockHttpServletResponse lastOwner = call(post(ws(ana, "/leave")), ana.token(), null);
        assertThat(lastOwner.getStatus()).isEqualTo(400);
        assertThat(body(lastOwner).get("message").asText()).isEqualTo("The last owner cannot leave the workspace");
        assertThat(call(delete(ws(ana, "/members/" + ana.userId())), ana.token(), null).getStatus()).isEqualTo(400);

        assertThat(call(put(ws(ana, "/members/" + bruno.userId())), ana.token(), Map.of("role", "OWNER")).getStatus()).isEqualTo(200);
        assertThat(call(post(ws(ana, "/leave")), ana.token(), null).getStatus()).as("agora há outro dono").isEqualTo(204);
        assertThat(call(get(ws(ana, "/accounts")), ana.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(put(ws(bruno, "/members/" + bruno.userId())), bruno.token(), Map.of("role", "MEMBER")).getStatus())
                .as("o último dono não se rebaixa").isEqualTo(400);
    }

    @Test
    @DisplayName("Quem não é membro recebe 404 em tudo (não revela se o espaço existe)")
    void nonMemberGets404() throws Exception {
        Session ana = register();
        Session eve = register("Eve Intrusa", email());
        for (String path : new String[]{"", "/accounts", "/categories", "/members", "/invites"}) {
            MockHttpServletResponse r = call(get(ws(ana, path)), eve.token(), null);
            assertThat(r.getStatus()).as(path).isEqualTo(404);
            assertThat(body(r).get("message").asText()).isEqualTo("Workspace not found");
        }
        assertThat(call(get("/api/workspaces/00000000-0000-0000-0000-000000000000/accounts"), eve.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/workspaces/nao-e-uuid/accounts"), eve.token(), null).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Excluir o espaço: só o dono; apaga contas, categorias e lançamentos; membros perdem o acesso")
    void deleteWorkspace() throws Exception {
        Session ana = register();
        String casa = body(call(post("/api/workspaces"), ana.token(), Map.of("name", "Casa"))).get("id").asText();
        Session bruno = register("Bruno Lima", email());
        String code = body(call(post("/api/workspaces/" + casa + "/invites"), ana.token(), null)).get("code").asText();
        accept(bruno, code);
        assertThat(call(delete("/api/workspaces/" + casa), ana.token(), null).getStatus()).isEqualTo(204);
        assertThat(call(get("/api/workspaces/" + casa + "/accounts"), bruno.token(), null).getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from accounts where workspace_id = ?::uuid", Integer.class, casa)).isZero();
    }

    @Test
    @DisplayName("Membro ou convite inexistente → 404; detalhes do espaço trazem o papel de quem pergunta")
    void notFoundCases() throws Exception {
        Session ana = register();
        assertThat(call(delete(ws(ana, "/members/00000000-0000-0000-0000-000000000000")), ana.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(put(ws(ana, "/members/00000000-0000-0000-0000-000000000000")), ana.token(), Map.of("role", "OWNER")).getStatus()).isEqualTo(404);
        assertThat(call(delete(ws(ana, "/invites/00000000-0000-0000-0000-000000000000")), ana.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(put(ws(ana, "/members/" + ana.userId())), ana.token(), Map.of("role", "BOSS")).getStatus()).isEqualTo(400);
        JsonNode w = body(call(get(ws(ana, "")), ana.token(), null));
        assertThat(w.get("role").asText()).isEqualTo("OWNER");
        assertThat(w.get("name").asText()).isEqualTo("Pessoal");
    }
}
