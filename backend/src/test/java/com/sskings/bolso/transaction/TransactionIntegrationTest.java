package com.sskings.bolso.transaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class TransactionIntegrationTest extends IntegrationTest {

    /** Espaço com duas contas (Corrente com 1000, Carteira com 0) e o id de uma categoria de cada tipo. */
    private record Fixture(Session s, String checking, String wallet, String expenseCat, String incomeCat) {}

    private Fixture fixture() throws Exception {
        Session s = register();
        String wallet = body(call(get(ws(s, "/accounts")), s.token(), null)).get(0).get("id").asText();
        String checking = body(call(post(ws(s, "/accounts")), s.token(),
                Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "1000.00"))).get("id").asText();
        JsonNode cats = body(call(get(ws(s, "/categories")), s.token(), null));
        String expense = null, income = null;
        for (JsonNode c : cats) {
            if (expense == null && c.get("kind").asText().equals("EXPENSE")) expense = c.get("id").asText();
            if (income == null && c.get("kind").asText().equals("INCOME")) income = c.get("id").asText();
        }
        return new Fixture(s, checking, wallet, expense, income);
    }

    private static Map<String, Object> tx(String type, String amount, String date, String description, String account, String category, String to) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("amount", amount);
        m.put("date", date);
        m.put("description", description);
        m.put("accountId", account);
        if (category != null) m.put("categoryId", category);
        if (to != null) m.put("toAccountId", to);
        return m;
    }

    private MockHttpServletResponse create(Fixture f, Map<String, Object> body) throws Exception {
        return call(post(ws(f.s(), "/transactions")), f.s().token(), body);
    }

    private BigDecimal balance(Fixture f, String accountId) throws Exception {
        return body(call(get(ws(f.s(), "/accounts/" + accountId)), f.s().token(), null)).get("balance").decimalValue();
    }

    private final String today = LocalDate.now().toString();

    @Test
    @DisplayName("Despesa, receita e transferência mudam os saldos certos; o saldo é sempre o calculado")
    void balances() throws Exception {
        Fixture f = fixture();
        assertThat(create(f, tx("EXPENSE", "120.40", today, "Mercado", f.checking(), f.expenseCat(), null)).getStatus()).isEqualTo(201);
        assertThat(create(f, tx("INCOME", "3500.00", today, "Salário", f.checking(), f.incomeCat(), null)).getStatus()).isEqualTo(201);
        assertThat(create(f, tx("TRANSFER", "200.00", today, "Saque", f.checking(), null, f.wallet())).getStatus()).isEqualTo(201);
        assertThat(balance(f, f.checking())).isEqualByComparingTo("4179.60");   // 1000 - 120.40 + 3500 - 200
        assertThat(balance(f, f.wallet())).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Lançamento com data futura não entra no saldo de hoje (é previsão)")
    void futureNotInBalance() throws Exception {
        Fixture f = fixture();
        create(f, tx("EXPENSE", "50.00", LocalDate.now().plusDays(10).toString(), "Conta futura", f.checking(), f.expenseCat(), null));
        assertThat(balance(f, f.checking())).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Resposta traz categoria, conta e quem lançou (útil no espaço compartilhado)")
    void responseShape() throws Exception {
        Fixture f = fixture();
        JsonNode t = body(create(f, tx("EXPENSE", "35.90", "2026-11-05", "  Almoço   no centro ", f.checking(), f.expenseCat(), null)));
        assertThat(t.get("description").asText()).isEqualTo("Almoço no centro");
        assertThat(t.get("amount").decimalValue()).isEqualByComparingTo("35.90");
        assertThat(t.get("date").asText()).isEqualTo("2026-11-05");
        assertThat(t.get("category").get("id").asText()).isEqualTo(f.expenseCat());
        assertThat(t.get("account").get("name").asText()).isEqualTo("Corrente");
        assertThat(t.get("createdBy").get("name").asText()).isEqualTo("Ana Souza");
        assertThat(t.has("workspaceId")).isFalse();
    }

    @Test
    @DisplayName("Validação de valor, data, descrição, tipo e combinação conta/categoria/destino")
    void validation() throws Exception {
        Fixture f = fixture();
        List<Map<String, Object>> invalid = List.of(
                tx("EXPENSE", "0", today, "Zero", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "-5.00", today, "Negativo", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "1.999", today, "3 casas", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "1000000000.00", today, "Grande demais", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", "2026-13-40", "Data inválida", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", "1999-12-31", "Antiga demais", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", LocalDate.now().plusYears(2).toString(), "Futura demais", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", today, "", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", today, "x".repeat(141), f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", today, "Linha\nfalsa", f.checking(), f.expenseCat(), null),
                tx("PIX", "10.00", today, "Tipo inválido", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", today, "Sem categoria", f.checking(), null, null),
                tx("EXPENSE", "10.00", today, "Categoria de receita", f.checking(), f.incomeCat(), null),
                tx("INCOME", "10.00", today, "Categoria de despesa", f.checking(), f.expenseCat(), null),
                tx("EXPENSE", "10.00", today, "Despesa com destino", f.checking(), f.expenseCat(), f.wallet()),
                tx("TRANSFER", "10.00", today, "Sem destino", f.checking(), null, null),
                tx("TRANSFER", "10.00", today, "Para a mesma conta", f.checking(), null, f.checking()),
                tx("TRANSFER", "10.00", today, "Com categoria", f.checking(), f.expenseCat(), f.wallet()));
        for (Map<String, Object> body : invalid) {
            assertThat(create(f, body).getStatus()).as(String.valueOf(body.get("description"))).isEqualTo(400);
        }
        Map<String, Object> extra = tx("EXPENSE", "10.00", today, "Com createdBy", f.checking(), f.expenseCat(), null);
        extra.put("createdBy", f.s().userId());
        assertThat(create(f, extra).getStatus()).isEqualTo(400);
        assertThat(balance(f, f.checking())).as("nada foi gravado").isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Conta ou categoria arquivada não recebe lançamento novo")
    void archivedCannotBeUsed() throws Exception {
        Fixture f = fixture();
        call(put(ws(f.s(), "/accounts/" + f.wallet())), f.s().token(),
                Map.of("name", "Carteira", "type", "CASH", "initialBalance", "0", "archived", true));
        MockHttpServletResponse r = create(f, tx("EXPENSE", "10.00", today, "Café", f.wallet(), f.expenseCat(), null));
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r).get("message").asText()).isEqualTo("Account is archived");
        assertThat(create(f, tx("TRANSFER", "10.00", today, "Para arquivada", f.checking(), null, f.wallet())).getStatus()).isEqualTo(400);

        call(put(ws(f.s(), "/categories/" + f.expenseCat())), f.s().token(), Map.of("name", "Velha", "color", "#000000", "archived", true));
        MockHttpServletResponse c = create(f, tx("EXPENSE", "10.00", today, "Café", f.checking(), f.expenseCat(), null));
        assertThat(body(c).get("message").asText()).isEqualTo("Category is archived");
    }

    @Test
    @DisplayName("Conta ou categoria de OUTRO espaço: tratada como inexistente; nada é gravado")
    void crossWorkspaceReferences() throws Exception {
        Fixture ana = fixture();
        Fixture eve = fixture();
        MockHttpServletResponse r1 = create(eve, tx("EXPENSE", "10.00", today, "Na conta da Ana", ana.checking(), eve.expenseCat(), null));
        assertThat(r1.getStatus()).isEqualTo(400);
        assertThat(body(r1).get("message").asText()).isEqualTo("Account not found");
        MockHttpServletResponse r2 = create(eve, tx("EXPENSE", "10.00", today, "Com categoria da Ana", eve.checking(), ana.expenseCat(), null));
        assertThat(body(r2).get("message").asText()).isEqualTo("Category not found");
        MockHttpServletResponse r3 = create(eve, tx("TRANSFER", "10.00", today, "Para a Ana", eve.checking(), null, ana.checking()));
        assertThat(body(r3).get("message").asText()).isEqualTo("Account not found");
        assertThat(balance(ana, ana.checking())).isEqualByComparingTo("1000.00");
        assertThat(balance(eve, eve.checking())).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Listar: mês, conta, categoria, tipo e busca no texto; mais recentes primeiro; paginado")
    void listAndFilter() throws Exception {
        Fixture f = fixture();
        create(f, tx("EXPENSE", "10.00", "2026-10-31", "Padaria", f.checking(), f.expenseCat(), null));
        create(f, tx("EXPENSE", "20.00", "2026-11-02", "Padaria do bairro", f.wallet(), f.expenseCat(), null));
        create(f, tx("INCOME", "30.00", "2026-11-03", "Freela 100%", f.checking(), f.incomeCat(), null));
        create(f, tx("TRANSFER", "40.00", "2026-11-04", "Reserva", f.checking(), null, f.wallet()));

        JsonNode nov = body(call(get(ws(f.s(), "/transactions?month=2026-11")), f.s().token(), null));
        assertThat(nov.get("items").findValuesAsText("description")).containsExactly("Reserva", "Freela 100%", "Padaria do bairro");
        assertThat(nov.get("total").asInt()).isEqualTo(3);

        assertThat(body(call(get(ws(f.s(), "/transactions?accountId=" + f.wallet())), f.s().token(), null)).get("items")
                .findValuesAsText("description")).as("transferência aparece na conta de destino").containsExactly("Reserva", "Padaria do bairro");
        assertThat(body(call(get(ws(f.s(), "/transactions?type=INCOME")), f.s().token(), null)).get("total").asInt()).isEqualTo(1);
        assertThat(body(call(get(ws(f.s(), "/transactions?categoryId=" + f.expenseCat())), f.s().token(), null)).get("total").asInt()).isEqualTo(2);
        assertThat(body(call(get(ws(f.s(), "/transactions?q=PADARIA")), f.s().token(), null)).get("total").asInt()).isEqualTo(2);
        assertThat(body(call(get(ws(f.s(), "/transactions")).param("q", "100%"), f.s().token(), null)).get("total").asInt())
                .as("% e _ na busca são literais").isEqualTo(1);
        assertThat(body(call(get(ws(f.s(), "/transactions")).param("q", "_"), f.s().token(), null)).get("total").asInt()).isZero();

        JsonNode page = body(call(get(ws(f.s(), "/transactions?size=2&page=1")), f.s().token(), null));
        assertThat(page.get("items")).hasSize(2);
        assertThat(page.get("total").asInt()).isEqualTo(4);
        assertThat(call(get(ws(f.s(), "/transactions?size=101")), f.s().token(), null).getStatus()).isEqualTo(400);
        assertThat(call(get(ws(f.s(), "/transactions?month=2026-13")), f.s().token(), null).getStatus()).isEqualTo(400);
        assertThat(call(get(ws(f.s(), "/transactions?q=" + "a".repeat(101))), f.s().token(), null).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Editar e excluir lançamento; o saldo acompanha")
    void updateAndDelete() throws Exception {
        Fixture f = fixture();
        String id = body(create(f, tx("EXPENSE", "100.00", today, "Conta de luz", f.checking(), f.expenseCat(), null))).get("id").asText();
        MockHttpServletResponse r = call(put(ws(f.s(), "/transactions/" + id)), f.s().token(),
                tx("EXPENSE", "150.00", today, "Conta de luz (corrigida)", f.wallet(), f.expenseCat(), null));
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(balance(f, f.checking())).isEqualByComparingTo("1000.00");
        assertThat(balance(f, f.wallet())).isEqualByComparingTo("-150.00");
        assertThat(call(get(ws(f.s(), "/transactions/" + id)), f.s().token(), null).getStatus()).isEqualTo(200);
        assertThat(call(delete(ws(f.s(), "/transactions/" + id)), f.s().token(), null).getStatus()).isEqualTo(204);
        assertThat(balance(f, f.wallet())).isEqualByComparingTo("0.00");
        assertThat(call(get(ws(f.s(), "/transactions/" + id)), f.s().token(), null).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Lançamentos simultâneos na mesma conta: todos gravados, saldo exato (nada de saldo guardado)")
    void concurrentCreates() throws Exception {
        Fixture f = fixture();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                results.add(pool.submit(() -> create(f, tx("EXPENSE", "1.25", today, "Café", f.checking(), f.expenseCat(), null)).getStatus()));
            }
            for (Future<Integer> r : results) assertThat(r.get()).isEqualTo(201);
        } finally {
            pool.shutdownNow();
        }
        assertThat(balance(f, f.checking())).isEqualByComparingTo("975.00");
    }

    @Test
    @DisplayName("Editar lançamento de conta/categoria arquivada DEPOIS: permitido; trocar PARA arquivada: recusado")
    void editWithArchivedReferences() throws Exception {
        Fixture f = fixture();
        String savings = body(call(post(ws(f.s(), "/accounts")), f.s().token(),
                Map.of("name", "Poupança", "type", "SAVINGS", "initialBalance", "0"))).get("id").asText();
        String expense = body(create(f, tx("EXPENSE", "10.00", today, "Antigo", f.wallet(), f.expenseCat(), null))).get("id").asText();
        String transfer = body(create(f, tx("TRANSFER", "5.00", today, "Reserva", f.checking(), null, f.wallet()))).get("id").asText();
        String income = body(create(f, tx("INCOME", "1.00", today, "Novo", f.checking(), f.incomeCat(), null))).get("id").asText();
        String toSavings = body(create(f, tx("TRANSFER", "1.00", today, "Para a poupança", f.checking(), null, savings))).get("id").asText();

        call(put(ws(f.s(), "/accounts/" + f.wallet())), f.s().token(), Map.of("name", "Carteira", "type", "CASH", "initialBalance", "0", "archived", true));
        call(put(ws(f.s(), "/categories/" + f.expenseCat())), f.s().token(), Map.of("name", "Velha", "color", "#000000", "archived", true));

        // mantendo a conta/categoria que já era do lançamento: pode editar
        assertThat(call(put(ws(f.s(), "/transactions/" + expense)), f.s().token(),
                tx("EXPENSE", "12.00", today, "Antigo (corrigido)", f.wallet(), f.expenseCat(), null)).getStatus()).isEqualTo(200);
        assertThat(call(put(ws(f.s(), "/transactions/" + transfer)), f.s().token(),
                tx("TRANSFER", "6.00", today, "Reserva", f.checking(), null, f.wallet())).getStatus()).isEqualTo(200);

        // trocando PARA a arquivada: recusado (origem e destino)
        MockHttpServletResponse toArchivedAccount = call(put(ws(f.s(), "/transactions/" + income)), f.s().token(),
                tx("INCOME", "1.00", today, "Novo", f.wallet(), f.incomeCat(), null));
        assertThat(body(toArchivedAccount).get("message").asText()).isEqualTo("Account is archived");
        MockHttpServletResponse toArchivedDest = call(put(ws(f.s(), "/transactions/" + toSavings)), f.s().token(),
                tx("TRANSFER", "1.00", today, "Para a poupança", f.checking(), null, f.wallet()));
        assertThat(body(toArchivedDest).get("message").asText()).isEqualTo("Account is archived");

        assertThat(body(call(get(ws(f.s(), "/transactions")).param("q", "   "), f.s().token(), null)).get("total").asInt())
                .as("busca em branco é ignorada").isEqualTo(4);
    }
}
