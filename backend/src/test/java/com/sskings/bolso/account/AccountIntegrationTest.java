package com.sskings.bolso.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AccountIntegrationTest extends IntegrationTest {

    private static Map<String, Object> account(String name, String type, Object initial) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("initialBalance", initial);
        return m;
    }

    @Test
    @DisplayName("Criar conta: aparece na lista com o saldo inicial; nomes não repetem (sem diferenciar maiúsculas)")
    void createAndList() throws Exception {
        Session s = register();
        MockHttpServletResponse r = call(post(ws(s, "/accounts")), s.token(), account("Nubank", "CHECKING", "1500.50"));
        assertThat(r.getStatus()).isEqualTo(201);
        JsonNode acc = body(r);
        assertThat(acc.get("name").asText()).isEqualTo("Nubank");
        assertThat(acc.get("balance").decimalValue()).isEqualByComparingTo("1500.50");

        JsonNode list = body(call(get(ws(s, "/accounts")), s.token(), null));
        assertThat(list.findValuesAsText("name")).containsExactly("Carteira", "Nubank");
        assertThat(call(post(ws(s, "/accounts")), s.token(), account("  NUBANK ", "SAVINGS", "0")).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Validação: nome, tipo, saldo inicial (2 casas, limite) e campos estranhos")
    void validation() throws Exception {
        Session s = register();
        List<Map<String, Object>> invalid = List.of(
                account("", "CHECKING", "0"),
                account("x".repeat(61), "CHECKING", "0"),
                account("Conta\r\nFalsa", "CHECKING", "0"),
                account("Conta", "CARTAO", "0"),
                account("Conta", null, "0"),
                account("Conta", "CHECKING", "10.123"),
                account("Conta", "CHECKING", "1000000000000"),
                account("Conta", "CHECKING", null));
        for (Map<String, Object> body : invalid) {
            assertThat(call(post(ws(s, "/accounts")), s.token(), body).getStatus()).as(String.valueOf(body)).isEqualTo(400);
        }
        Map<String, Object> extra = account("Conta", "CHECKING", "0");
        extra.put("workspaceId", "00000000-0000-0000-0000-000000000000");
        assertThat(call(post(ws(s, "/accounts")), s.token(), extra).getStatus()).isEqualTo(400);
        assertThat(call(post(ws(s, "/accounts")), s.token(), account("Negativa", "CHECKING", "-250.00")).getStatus())
                .as("saldo inicial negativo é permitido (cheque especial)").isEqualTo(201);
    }

    @Test
    @DisplayName("Editar e arquivar; excluir só conta sem lançamentos")
    void updateArchiveDelete() throws Exception {
        Session s = register();
        String id = body(call(post(ws(s, "/accounts")), s.token(), account("Itaú", "CHECKING", "0"))).get("id").asText();
        Map<String, Object> upd = account("Itaú PJ", "SAVINGS", "10.00");
        upd.put("archived", true);
        MockHttpServletResponse r = call(put(ws(s, "/accounts/" + id)), s.token(), upd);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r).get("archived").asBoolean()).isTrue();
        assertThat(body(r).get("type").asText()).isEqualTo("SAVINGS");

        assertThat(call(delete(ws(s, "/accounts/" + id)), s.token(), null).getStatus()).isEqualTo(204);
        assertThat(call(get(ws(s, "/accounts/" + id)), s.token(), null).getStatus()).isEqualTo(404);

        String carteira = body(call(get(ws(s, "/accounts")), s.token(), null)).get(0).get("id").asText();
        String cat = body(call(get(ws(s, "/categories")), s.token(), null)).get(0).get("id").asText();
        call(post(ws(s, "/transactions")), s.token(), Map.of("type", "EXPENSE", "amount", "10.00", "date", "2026-11-01",
                "description", "Café", "accountId", carteira, "categoryId", cat));
        MockHttpServletResponse used = call(delete(ws(s, "/accounts/" + carteira)), s.token(), null);
        assertThat(used.getStatus()).isEqualTo(409);
        assertThat(body(used).get("message").asText()).isEqualTo("Account has transactions; archive it instead");
    }

    @Test
    @DisplayName("Renomear: para o nome de outra conta → 409; mudar só maiúsculas da própria → ok")
    void renameRules() throws Exception {
        Session s = register();
        String id = body(call(post(ws(s, "/accounts")), s.token(), account("Itaú", "CHECKING", "0"))).get("id").asText();
        assertThat(call(put(ws(s, "/accounts/" + id)), s.token(), account("carteira", "CHECKING", "0")).getStatus()).isEqualTo(409);
        assertThat(call(put(ws(s, "/accounts/" + id)), s.token(), account("ITAÚ", "CHECKING", "0")).getStatus()).isEqualTo(200);
    }
}
