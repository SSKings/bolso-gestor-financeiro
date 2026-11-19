package com.sskings.bolso.category;

import tools.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CategoryIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("Categorias padrão; criar; mesmo nome em tipos diferentes pode, no mesmo tipo não")
    void createAndUniqueness() throws Exception {
        Session s = register();
        MockHttpServletResponse r = call(post(ws(s, "/categories")), s.token(), Map.of("name", "Pets", "kind", "EXPENSE", "color", "#AABBCC"));
        assertThat(r.getStatus()).isEqualTo(201);
        assertThat(body(r).get("color").asString()).isEqualTo("#aabbcc");
        assertThat(call(post(ws(s, "/categories")), s.token(), Map.of("name", "pets", "kind", "EXPENSE", "color", "#000000")).getStatus()).isEqualTo(409);
        assertThat(call(post(ws(s, "/categories")), s.token(), Map.of("name", "Pets", "kind", "INCOME", "color", "#000000")).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Validação: nome, tipo, cor no formato #rrggbb")
    void validation() throws Exception {
        Session s = register();
        List<Map<String, Object>> invalid = List.of(
                Map.of("name", "", "kind", "EXPENSE", "color", "#000000"),
                Map.of("name", "x".repeat(41), "kind", "EXPENSE", "color", "#000000"),
                Map.of("name", "A", "kind", "TRANSFER", "color", "#000000"),
                Map.of("name", "A", "kind", "EXPENSE", "color", "red"),
                Map.of("name", "A", "kind", "EXPENSE", "color", "#000000;background:url(x)"),
                Map.of("name", "A\u0007", "kind", "EXPENSE", "color", "#000000"));
        for (Map<String, Object> body : invalid) {
            assertThat(call(post(ws(s, "/categories")), s.token(), body).getStatus()).as(body.toString()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("Editar nome/cor e arquivar; o tipo não muda; excluir só se não tiver lançamentos")
    void updateAndDelete() throws Exception {
        Session s = register();
        String id = body(call(post(ws(s, "/categories")), s.token(), Map.of("name", "Pets", "kind", "EXPENSE", "color", "#000000"))).get("id").asString();
        MockHttpServletResponse r = call(put(ws(s, "/categories/" + id)), s.token(), Map.of("name", "Animais", "color", "#ff0000", "archived", true));
        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode c = body(r);
        assertThat(c.get("name").asString()).isEqualTo("Animais");
        assertThat(c.get("archived").asBoolean()).isTrue();
        assertThat(call(put(ws(s, "/categories/" + id)), s.token(), Map.of("name", "X", "color", "#ff0000", "archived", false, "kind", "INCOME")).getStatus())
                .as("kind não é editável").isEqualTo(400);
        assertThat(call(delete(ws(s, "/categories/" + id)), s.token(), null).getStatus()).isEqualTo(204);

        JsonNode cats = body(call(get(ws(s, "/categories")), s.token(), null));
        String used = cats.get(0).get("id").asString();
        String account = body(call(get(ws(s, "/accounts")), s.token(), null)).get(0).get("id").asString();
        String type = cats.get(0).get("kind").asString();
        call(post(ws(s, "/transactions")), s.token(), Map.of("type", type, "amount", "10.00", "date", "2026-11-01",
                "description", "Algo", "accountId", account, "categoryId", used));
        MockHttpServletResponse inUse = call(delete(ws(s, "/categories/" + used)), s.token(), null);
        assertThat(inUse.getStatus()).isEqualTo(409);
        assertThat(body(inUse).get("message").asString()).isEqualTo("Category has transactions; archive it instead");
    }

    @Test
    @DisplayName("Renomear categoria: para nome existente do mesmo tipo → 409; só maiúsculas → ok")
    void renameRules() throws Exception {
        Session s = register();
        String id = body(call(post(ws(s, "/categories")), s.token(), Map.of("name", "Pets", "kind", "EXPENSE", "color", "#000000"))).get("id").asString();
        assertThat(call(put(ws(s, "/categories/" + id)), s.token(), Map.of("name", "mercado", "color", "#000000")).getStatus()).isEqualTo(409);
        assertThat(call(put(ws(s, "/categories/" + id)), s.token(), Map.of("name", "PETS", "color", "#000000")).getStatus()).isEqualTo(200);
        assertThat(call(put(ws(s, "/categories/00000000-0000-0000-0000-000000000000")), s.token(), Map.of("name", "X", "color", "#000000")).getStatus()).isEqualTo(404);
    }
}
