package com.sskings.bolso.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DashboardIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("Painel do mês: entradas, saídas, resultado, gastos por categoria (maior primeiro) e saldos")
    void monthSummary() throws Exception {
        Session s = register();
        String wallet = body(call(get(ws(s, "/accounts")), s.token(), null)).get(0).get("id").asText();
        String checking = body(call(post(ws(s, "/accounts")), s.token(),
                Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "500.00"))).get("id").asText();
        JsonNode cats = body(call(get(ws(s, "/categories")), s.token(), null));
        String food = null, market = null, salary = null;
        for (JsonNode c : cats) {
            switch (c.get("name").asText()) {
                case "Alimentação" -> food = c.get("id").asText();
                case "Mercado" -> market = c.get("id").asText();
                case "Salário" -> salary = c.get("id").asText();
                default -> { }
            }
        }
        String[][] txs = {
                {"EXPENSE", "40.00", "2026-11-03", food, null},
                {"EXPENSE", "60.00", "2026-11-10", food, null},
                {"EXPENSE", "250.00", "2026-11-12", market, null},
                {"INCOME", "3000.00", "2026-11-05", salary, null},
                {"EXPENSE", "999.00", "2026-10-30", market, null},          // outro mês
                {"TRANSFER", "100.00", "2026-11-15", null, wallet}};      // transferência não é gasto
        for (String[] t : txs) {
            Map<String, Object> body = new java.util.HashMap<>(Map.of("type", t[0], "amount", t[1], "date", t[2],
                    "description", "x", "accountId", checking));
            if (t[3] != null) body.put("categoryId", t[3]);
            if (t[4] != null) body.put("toAccountId", t[4]);
            assertThat(call(post(ws(s, "/transactions")), s.token(), body).getStatus()).isEqualTo(201);
        }

        JsonNode d = body(call(get(ws(s, "/dashboard?month=2026-11")), s.token(), null));
        assertThat(d.get("month").asText()).isEqualTo("2026-11");
        assertThat(d.get("income").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(d.get("expense").decimalValue()).isEqualByComparingTo("350.00");
        assertThat(d.get("net").decimalValue()).isEqualByComparingTo("2650.00");
        assertThat(d.get("expensesByCategory").findValuesAsText("name")).containsExactly("Mercado", "Alimentação");
        assertThat(d.get("expensesByCategory").get(0).get("amount").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(d.get("expensesByCategory").get(0).get("color").asText()).startsWith("#");
        assertThat(d.get("totalBalance").decimalValue()).isEqualByComparingTo("2151.00");   // 500 + 3000 - 350 - 999 (out.); a transferência se anula
        assertThat(d.get("accounts")).hasSize(2);

        JsonNode empty = body(call(get(ws(s, "/dashboard?month=2025-01")), s.token(), null));
        assertThat(empty.get("expense").decimalValue()).isEqualByComparingTo("0");
        assertThat(empty.get("expensesByCategory")).isEmpty();
        assertThat(call(get(ws(s, "/dashboard?month=abc")), s.token(), null).getStatus()).isEqualTo(400);
        assertThat(call(get(ws(s, "/dashboard")), s.token(), null).getStatus()).as("sem mês: o atual").isEqualTo(200);
    }
}
