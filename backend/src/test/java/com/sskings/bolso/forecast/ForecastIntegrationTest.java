package com.sskings.bolso.forecast;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Previsão do mês ("quanto vai sobrar"). Hoje: 10/11/2026. */
class ForecastIntegrationTest extends IntegrationTest {

    @BeforeEach
    void today() {
        clock.set(Instant.parse("2026-11-10T15:00:00Z"));
    }

    private static Map<String, Object> rec(String type, String desc, String amount, int day, String cat, String acc, String start, String end) {
        Map<String, Object> m = new HashMap<>(Map.of("type", type, "description", desc, "amount", amount, "dayOfMonth", day,
                "categoryId", cat, "accountId", acc, "startMonth", start));
        if (end != null) m.put("endMonth", end);
        return m;
    }

    @Test
    @DisplayName("Saldo previsto no fim do mês = hoje + o que entra − contas fixas (atrasadas também) − agendados − faturas do mês")
    void projection() throws Exception {
        Session s = register();
        Map<String, String> c = new HashMap<>();
        for (JsonNode x : body(call(get(ws(s, "/categories")), s.token(), null))) c.put(x.get("name").asText() + "/" + x.get("kind").asText(), x.get("id").asText());
        String acc = body(call(post(ws(s, "/accounts")), s.token(), Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "1000.00"))).get("id").asText();
        String rent = c.get("Moradia/EXPENSE"), salary = c.get("Salário/INCOME"), fun = c.get("Lazer/EXPENSE"), food = c.get("Alimentação/EXPENSE");

        call(post(ws(s, "/transactions")), s.token(), Map.of("type", "INCOME", "amount", "3000.00", "date", "2026-11-05",
                "description", "Salário", "accountId", acc, "categoryId", salary));                                  // saldo 4000
        call(post(ws(s, "/transactions")), s.token(), Map.of("type", "EXPENSE", "amount", "200.00", "date", "2026-11-20",
                "description", "Presente agendado", "accountId", acc, "categoryId", fun));                          // agendado: −200
        call(post(ws(s, "/transactions")), s.token(), Map.of("type", "EXPENSE", "amount", "70.00", "date", "2026-12-02",
                "description", "Mês que vem", "accountId", acc, "categoryId", fun));                                // fora do mês
        call(post(ws(s, "/recurrences")), s.token(), rec("EXPENSE", "Aluguel", "1500.00", 15, rent, acc, "2026-01", null));       // −1500
        call(post(ws(s, "/recurrences")), s.token(), rec("EXPENSE", "Internet", "100.00", 5, rent, acc, "2026-01", null));        // atrasada: −100
        call(post(ws(s, "/recurrences")), s.token(), rec("INCOME", "Freela", "500.00", 25, salary, acc, "2026-01", null));        // +500
        String streaming = body(call(post(ws(s, "/recurrences")), s.token(), rec("EXPENSE", "Streaming", "40.00", 8, fun, acc, "2026-01", null))).get("id").asText();
        call(post(ws(s, "/recurrences/" + streaming + "/occurrences/2026-11/confirm")), s.token(),
                Map.of("amount", "40.00", "date", "2026-11-08", "accountId", acc));                                 // paga: saldo 3960
        call(post(ws(s, "/recurrences")), s.token(), rec("EXPENSE", "Curso encerrado", "300.00", 20, rent, acc, "2026-01", "2026-09"));
        Map<String, Object> paused = rec("EXPENSE", "Academia pausada", "120.00", 28, fun, acc, "2026-01", null);
        String pausedId = body(call(post(ws(s, "/recurrences")), s.token(), paused)).get("id").asText();
        paused.put("active", false);
        call(put(ws(s, "/recurrences/" + pausedId)), s.token(), paused);

        String card = body(call(post(ws(s, "/cards")), s.token(), Map.of("name", "Nubank", "closingDay", 10, "dueDay", 17, "creditLimit", "5000.00"))).get("id").asText();
        call(post(ws(s, "/cards/" + card + "/purchases")), s.token(), Map.of("description", "Mercado", "amount", "300.00",
                "installments", 1, "date", "2026-11-03", "categoryId", food));                                      // fatura nov vence 17/11
        call(post(ws(s, "/cards/" + card + "/statements/2026-11/payments")), s.token(),
                Map.of("accountId", acc, "amount", "100.00", "date", "2026-11-09"));                               // saldo 3860; falta 200

        JsonNode f = body(call(get(ws(s, "/forecast")), s.token(), null));
        assertThat(f.get("month").asText()).isEqualTo("2026-11");
        assertThat(f.get("today").asText()).isEqualTo("2026-11-10");
        assertThat(f.get("currentBalance").decimalValue()).isEqualByComparingTo("3860.00");
        assertThat(f.get("pendingIncome").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(f.get("pendingExpense").decimalValue()).isEqualByComparingTo("2000.00");   // 200 + 1500 + 100 + 200
        assertThat(f.get("projectedBalance").decimalValue()).isEqualByComparingTo("2360.00");
        assertThat(f.get("items").findValuesAsText("description")).containsExactlyInAnyOrder(
                "Presente agendado", "Aluguel", "Internet", "Freela", "Fatura Nubank");
        assertThat(f.get("items").findValuesAsText("kind")).contains("SCHEDULED", "BILL", "CARD_STATEMENT");
    }

    @Test
    @DisplayName("Espaço vazio: previsão = saldo; intrusa não vê a previsão de outro espaço")
    void emptyAndIsolation() throws Exception {
        Session s = register();
        JsonNode f = body(call(get(ws(s, "/forecast")), s.token(), null));
        assertThat(f.get("projectedBalance").decimalValue()).isEqualByComparingTo("0");
        assertThat(f.get("items")).isEmpty();
        Session eve = register();
        assertThat(call(get(ws(s, "/forecast")), eve.token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Cartão que vence antes do fechamento: a fatura de outubro vence em novembro e entra na previsão de novembro")
    void statementDueNextMonth() throws Exception {
        Session s = register();
        String food = null;
        for (JsonNode x : body(call(get(ws(s, "/categories")), s.token(), null))) if (x.get("name").asText().equals("Alimentação")) food = x.get("id").asText();
        String card = body(call(post(ws(s, "/cards")), s.token(), Map.of("name", "Inter", "closingDay", 25, "dueDay", 5, "creditLimit", "1000.00"))).get("id").asText();
        call(post(ws(s, "/cards/" + card + "/purchases")), s.token(), Map.of("description", "Outubro", "amount", "80.00",
                "installments", 1, "date", "2026-10-20", "categoryId", food));      // fatura de out, vence 05/11
        call(post(ws(s, "/cards/" + card + "/purchases")), s.token(), Map.of("description", "Novembro", "amount", "30.00",
                "installments", 1, "date", "2026-11-08", "categoryId", food));      // fatura de nov, vence 05/12: fora
        JsonNode f = body(call(get(ws(s, "/forecast")), s.token(), null));
        assertThat(f.get("pendingExpense").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(f.get("items").get(0).get("date").asText()).isEqualTo("2026-11-05");
    }
}
