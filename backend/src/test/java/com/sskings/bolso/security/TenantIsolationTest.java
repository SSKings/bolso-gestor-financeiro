package com.sskings.bolso.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Varredura de isolamento entre espaços (o defeito central do projeto antigo: o id do usuário vinha do corpo).
 * A intrusa tenta TODAS as rotas de recursos da vítima, pelo espaço da vítima e pelo próprio espaço com ids da
 * vítima. Tudo tem de dar 404 e nada da vítima pode mudar.
 */
class TenantIsolationTest extends IntegrationTest {

    @Test
    @DisplayName("Intrusa não lê, altera nem apaga nada de outro espaço — por nenhuma rota")
    void everyRouteIsIsolated() throws Exception {
        Session victim = register();
        String account = body(call(get(ws(victim, "/accounts")), victim.token(), null)).get(0).get("id").asText();
        String category = body(call(get(ws(victim, "/categories")), victim.token(), null)).get(0).get("id").asText();
        String kind = body(call(get(ws(victim, "/categories")), victim.token(), null)).get(0).get("kind").asText();
        String tx = body(call(post(ws(victim, "/transactions")), victim.token(), Map.of("type", kind, "amount", "10.00",
                "date", "2026-11-01", "description", "Segredo", "accountId", account, "categoryId", category))).get("id").asText();
        String invite = body(call(get(ws(victim, "/invites")), victim.token(), null)).isEmpty()
                ? body(call(post(ws(victim, "/invites")), victim.token(), null)).get("id").asText() : null;

        Session eve = register("Eve Intrusa", email());
        String vw = "/api/workspaces/" + victim.workspaceId();
        String ew = "/api/workspaces/" + eve.workspaceId();
        Map<String, Object> accBody = Map.of("name", "Hack", "type", "CASH", "initialBalance", "0", "archived", false);
        Map<String, Object> catBody = Map.of("name", "Hack", "color", "#000000", "archived", false);
        Map<String, Object> txBody = Map.of("type", kind, "amount", "1.00", "date", "2026-11-01", "description", "Hack",
                "accountId", account, "categoryId", category);

        List<Object[]> attempts = List.of(
                new Object[]{get(vw), null}, new Object[]{put(vw), Map.of("name", "Hack")}, new Object[]{delete(vw), null},
                new Object[]{get(vw + "/accounts"), null}, new Object[]{post(vw + "/accounts"), accBody},
                new Object[]{get(vw + "/accounts/" + account), null}, new Object[]{put(vw + "/accounts/" + account), accBody},
                new Object[]{delete(vw + "/accounts/" + account), null},
                new Object[]{get(vw + "/categories"), null}, new Object[]{put(vw + "/categories/" + category), catBody},
                new Object[]{delete(vw + "/categories/" + category), null},
                new Object[]{get(vw + "/transactions"), null}, new Object[]{post(vw + "/transactions"), txBody},
                new Object[]{get(vw + "/transactions/" + tx), null}, new Object[]{put(vw + "/transactions/" + tx), txBody},
                new Object[]{delete(vw + "/transactions/" + tx), null},
                new Object[]{get(vw + "/dashboard"), null}, new Object[]{get(vw + "/members"), null},
                new Object[]{post(vw + "/invites"), null}, new Object[]{post(vw + "/leave"), null},
                new Object[]{delete(vw + "/members/" + victim.userId()), null},
                new Object[]{delete(vw + "/invites/" + invite), null},
                // pelo próprio espaço, com ids da vítima
                new Object[]{get(ew + "/accounts/" + account), null}, new Object[]{put(ew + "/accounts/" + account), accBody},
                new Object[]{delete(ew + "/accounts/" + account), null},
                new Object[]{put(ew + "/categories/" + category), catBody}, new Object[]{delete(ew + "/categories/" + category), null},
                new Object[]{get(ew + "/transactions/" + tx), null}, new Object[]{put(ew + "/transactions/" + tx), txBody},
                new Object[]{delete(ew + "/transactions/" + tx), null},
                new Object[]{delete(ew + "/members/" + victim.userId()), null},
                new Object[]{delete(ew + "/invites/" + invite), null});
        for (Object[] a : attempts) {
            MockHttpServletRequestBuilder req = (MockHttpServletRequestBuilder) a[0];
            String label = req.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod() + " "
                    + req.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();
            MockHttpServletResponse r = call(req, eve.token(), a[1]);
            assertThat(r.getStatus()).as(label).isIn(400, 404);
            assertThat(r.getContentAsString()).as(label).doesNotContain("Segredo");
        }

        // nada da vítima mudou
        JsonNode t = body(call(get(ws(victim, "/transactions/" + tx)), victim.token(), null));
        assertThat(t.get("description").asText()).isEqualTo("Segredo");
        assertThat(body(call(get(ws(victim, "/accounts")), victim.token(), null)).findValuesAsText("name")).containsExactly("Carteira");
        assertThat(body(call(get(ws(victim, "/members")), victim.token(), null))).hasSize(1);
        assertThat(body(call(get(ws(victim, "/invites")), victim.token(), null))).hasSize(1);
        assertThat(body(call(get("/api/me"), victim.token(), null)).get("workspaces").get(0).get("name").asText()).isEqualTo("Pessoal");
    }
}
