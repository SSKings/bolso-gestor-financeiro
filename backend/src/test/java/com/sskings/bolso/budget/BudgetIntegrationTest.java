package com.sskings.bolso.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class BudgetIntegrationTest extends IntegrationTest {

    private Map<String, String> cats(Session s) throws Exception {
        Map<String, String> m = new HashMap<>();
        for (JsonNode c : body(call(get(ws(s, "/categories")), s.token(), null))) m.put(c.get("name").asText() + "/" + c.get("kind").asText(), c.get("id").asText());
        return m;
    }

    private void spend(Session s, String account, String category, String amount, String date) throws Exception {
        assertThat(call(post(ws(s, "/transactions")), s.token(), Map.of("type", "EXPENSE", "amount", amount, "date", date,
                "description", "Gasto", "accountId", account, "categoryId", category)).getStatus()).isEqualTo(201);
    }

    private JsonNode budgets(Session s, String month) throws Exception {
        return body(call(get(ws(s, "/budgets?month=" + month)), s.token(), null));
    }

    private JsonNode of(JsonNode list, String name) {
        for (JsonNode b : list) if (b.get("category").get("name").asText().equals(name)) return b;
        throw new AssertionError("sem orçamento de " + name);
    }

    @Test
    @DisplayName("Orçamento do mês: gasto (conta e cartão), percentual e alerta — OK, ATENÇÃO (80%) e ESTOURADO")
    void status() throws Exception {
        Session s = register();
        Map<String, String> c = cats(s);
        String account = body(call(get(ws(s, "/accounts")), s.token(), null)).get(0).get("id").asText();
        String card = body(call(post(ws(s, "/cards")), s.token(), Map.of("name", "Nubank", "closingDay", 10, "dueDay", 17, "creditLimit", "5000.00"))).get("id").asText();
        for (String[] b : new String[][]{{"Alimentação/EXPENSE", "500.00"}, {"Lazer/EXPENSE", "100.00"}, {"Mercado/EXPENSE", "1000.00"}}) {
            assertThat(call(put(ws(s, "/budgets/" + c.get(b[0]))), s.token(), Map.of("amount", b[1])).getStatus()).isEqualTo(200);
        }
        spend(s, account, c.get("Alimentação/EXPENSE"), "300.00", "2026-11-04");
        call(post(ws(s, "/cards/" + card + "/purchases")), s.token(), Map.of("description", "Restaurante", "amount", "150.00",
                "installments", 1, "date", "2026-11-06", "categoryId", c.get("Alimentação/EXPENSE")));        // fatura de nov
        spend(s, account, c.get("Lazer/EXPENSE"), "120.00", "2026-11-02");
        spend(s, account, c.get("Alimentação/EXPENSE"), "999.00", "2026-10-15");                              // outro mês

        JsonNode nov = budgets(s, "2026-11");
        JsonNode food = of(nov, "Alimentação");
        assertThat(food.get("spent").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(food.get("percent").asInt()).isEqualTo(90);
        assertThat(food.get("status").asText()).isEqualTo("WARNING");
        assertThat(of(nov, "Lazer").get("status").asText()).isEqualTo("EXCEEDED");
        assertThat(of(nov, "Lazer").get("remaining").decimalValue()).isEqualByComparingTo("-20.00");
        assertThat(of(nov, "Mercado").get("status").asText()).isEqualTo("OK");
        assertThat(nov.get(0).get("category").get("name").asText()).as("o mais apertado primeiro").isEqualTo("Lazer");
        assertThat(of(budgets(s, "2026-10"), "Alimentação").get("status").asText()).isEqualTo("EXCEEDED");
    }

    @Test
    @DisplayName("Criar, alterar e remover; só categoria de despesa do próprio espaço; valor válido")
    void crud() throws Exception {
        Session s = register();
        Map<String, String> c = cats(s);
        String food = c.get("Alimentação/EXPENSE");
        assertThat(call(put(ws(s, "/budgets/" + food)), s.token(), Map.of("amount", "500.00")).getStatus()).isEqualTo(200);
        assertThat(call(put(ws(s, "/budgets/" + food)), s.token(), Map.of("amount", "650.00")).getStatus()).isEqualTo(200);
        assertThat(budgets(s, "2026-11")).hasSize(1);
        assertThat(budgets(s, "2026-11").get(0).get("amount").decimalValue()).isEqualByComparingTo("650.00");

        for (String amount : new String[]{"0", "-1.00", "1.001", "1000000000.00"}) {
            assertThat(call(put(ws(s, "/budgets/" + food)), s.token(), Map.of("amount", amount)).getStatus()).as(amount).isEqualTo(400);
        }
        MockHttpServletResponse income = call(put(ws(s, "/budgets/" + c.get("Salário/INCOME"))), s.token(), Map.of("amount", "10.00"));
        assertThat(body(income).get("message").asText()).isEqualTo("Budgets are for expense categories");

        Session eve = register();
        MockHttpServletResponse foreign = call(put(ws(eve, "/budgets/" + food)), eve.token(), Map.of("amount", "1.00"));
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(body(foreign).get("message").asText()).isEqualTo("Category not found");
        assertThat(call(get(ws(s, "/budgets?month=2026-11")), eve.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(delete(ws(s, "/budgets/" + food)), eve.token(), null).getStatus()).isEqualTo(404);

        assertThat(call(delete(ws(s, "/budgets/" + food)), s.token(), null).getStatus()).isEqualTo(204);
        assertThat(budgets(s, "2026-11")).isEmpty();
        assertThat(call(delete(ws(s, "/budgets/" + food)), s.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(get(ws(s, "/budgets?month=2026-13")), s.token(), null).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Excluir a categoria leva junto o orçamento dela")
    void categoryDeleteRemovesBudget() throws Exception {
        Session s = register();
        String pets = body(call(post(ws(s, "/categories")), s.token(), Map.of("name", "Pets", "kind", "EXPENSE", "color", "#000000"))).get("id").asText();
        call(put(ws(s, "/budgets/" + pets)), s.token(), Map.of("amount", "200.00"));
        assertThat(call(delete(ws(s, "/categories/" + pets)), s.token(), null).getStatus()).isEqualTo(204);
        assertThat(budgets(s, "2026-11")).isEmpty();
    }
}
