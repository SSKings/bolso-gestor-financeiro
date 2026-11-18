package com.sskings.bolso.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CardIntegrationTest extends IntegrationTest {

    private record Fx(Session s, String card, String account, String food, String salary) {}

    private static Map<String, Object> card(String name, Object last4, int closing, int due, String limit) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        if (last4 != null) m.put("last4", last4);
        m.put("closingDay", closing);
        m.put("dueDay", due);
        m.put("creditLimit", limit);
        return m;
    }

    private static Map<String, Object> purchase(String desc, String amount, int installments, String date, String category) {
        Map<String, Object> m = new HashMap<>();
        m.put("description", desc);
        m.put("amount", amount);
        m.put("installments", installments);
        m.put("date", date);
        m.put("categoryId", category);
        return m;
    }

    /** Cartão "Nubank" que fecha dia 10 e vence dia 17, limite 5000; conta corrente com 3000. */
    private Fx fixture() throws Exception {
        Session s = register();
        String cardId = body(call(post(ws(s, "/cards")), s.token(), card("Nubank", "1234", 10, 17, "5000.00"))).get("id").asText();
        String account = body(call(post(ws(s, "/accounts")), s.token(),
                Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "3000.00"))).get("id").asText();
        String food = null, salary = null;
        for (JsonNode c : body(call(get(ws(s, "/categories")), s.token(), null))) {
            if (c.get("name").asText().equals("Alimentação")) food = c.get("id").asText();
            if (c.get("name").asText().equals("Salário")) salary = c.get("id").asText();
        }
        return new Fx(s, cardId, account, food, salary);
    }

    private MockHttpServletResponse buy(Fx f, Map<String, Object> body) throws Exception {
        return call(post(ws(f.s(), "/cards/" + f.card() + "/purchases")), f.s().token(), body);
    }

    private JsonNode statement(Fx f, String month) throws Exception {
        MockHttpServletResponse r = call(get(ws(f.s(), "/cards/" + f.card() + "/statements/" + month)), f.s().token(), null);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private BigDecimal available(Fx f) throws Exception {
        return body(call(get(ws(f.s(), "/cards/" + f.card())), f.s().token(), null)).get("available").decimalValue();
    }

    @Test
    @DisplayName("Cartão guarda só os 4 últimos dígitos: número completo é recusado")
    void onlyLast4() throws Exception {
        Session s = register();
        JsonNode c = body(call(post(ws(s, "/cards")), s.token(), card("Inter", "9876", 5, 12, "1000.00")));
        assertThat(c.get("last4").asText()).isEqualTo("9876");
        assertThat(c.get("available").decimalValue()).isEqualByComparingTo("1000.00");
        List<Map<String, Object>> invalid = List.of(
                card("A", "4111111111111111", 5, 12, "1000.00"),
                card("A", "12345", 5, 12, "1000.00"),
                card("A", "12a4", 5, 12, "1000.00"),
                card("A", null, 0, 12, "1000.00"),
                card("A", null, 29, 12, "1000.00"),
                card("A", null, 5, 31, "1000.00"),
                card("A", null, 5, 12, "-1.00"),
                card("A", null, 5, 12, "10.999"),
                card("", null, 5, 12, "1000.00"),
                card("Cartão\nfalso", null, 5, 12, "1000.00"));
        for (Map<String, Object> b : invalid) {
            assertThat(call(post(ws(s, "/cards")), s.token(), b).getStatus()).as(b.toString()).isEqualTo(400);
        }
        Map<String, Object> withNumber = card("A", null, 5, 12, "1000.00");
        withNumber.put("number", "4111111111111111");
        assertThat(call(post(ws(s, "/cards")), s.token(), withNumber).getStatus()).as("campo 'number' não existe").isEqualTo(400);
        assertThat(call(post(ws(s, "/cards")), s.token(), card("inter", null, 5, 12, "0")).getStatus()).isEqualTo(409);
        assertThat(call(post(ws(s, "/cards")), s.token(), card("Sem final", null, 5, 12, "0")).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("Compra à vista: antes/no fechamento entra na fatura do mês; depois, na seguinte")
    void singlePurchase() throws Exception {
        Fx f = fixture();
        assertThat(buy(f, purchase("Mercado", "120.00", 1, "2026-11-10", f.food())).getStatus()).isEqualTo(201);
        assertThat(buy(f, purchase("Padaria", "15.50", 1, "2026-11-11", f.food())).getStatus()).isEqualTo(201);

        JsonNode nov = statement(f, "2026-11");
        assertThat(nov.get("total").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(nov.get("closingDate").asText()).isEqualTo("2026-11-10");
        assertThat(nov.get("dueDate").asText()).isEqualTo("2026-11-17");
        assertThat(nov.get("items").findValuesAsText("description")).containsExactly("Mercado");
        assertThat(statement(f, "2026-12").get("total").decimalValue()).isEqualByComparingTo("15.50");
    }

    @Test
    @DisplayName("Parcelado: 3× de R$ 100 vira 33,34 + 33,33 + 33,33 em três faturas seguidas")
    void installments() throws Exception {
        Fx f = fixture();
        JsonNode p = body(buy(f, purchase("Geladeira", "100.00", 3, "2026-11-20", f.food())));
        assertThat(p.get("installments").asInt()).isEqualTo(3);
        assertThat(p.get("parts").findValuesAsText("statement")).containsExactly("2026-12", "2027-01", "2027-02");

        JsonNode dez = statement(f, "2026-12");
        assertThat(dez.get("items").get(0).get("number").asInt()).isEqualTo(1);
        assertThat(dez.get("items").get(0).get("of").asInt()).isEqualTo(3);
        assertThat(dez.get("total").decimalValue()).isEqualByComparingTo("33.34");
        assertThat(statement(f, "2027-01").get("total").decimalValue()).isEqualByComparingTo("33.33");
        assertThat(statement(f, "2027-02").get("total").decimalValue()).isEqualByComparingTo("33.33");
        assertThat(statement(f, "2027-03").get("total").decimalValue()).isEqualByComparingTo("0");
        assertThat(statement(f, "2027-03").get("items")).isEmpty();
    }

    @Test
    @DisplayName("Validação da compra: valor, parcelas (1 a 48, sem parcela de centavo zero), data, categoria de despesa")
    void purchaseValidation() throws Exception {
        Fx f = fixture();
        List<Map<String, Object>> invalid = List.of(
                purchase("X", "0", 1, "2026-11-01", f.food()),
                purchase("X", "10.00", 0, "2026-11-01", f.food()),
                purchase("X", "10.00", 49, "2026-11-01", f.food()),
                purchase("X", "0.02", 3, "2026-11-01", f.food()),
                purchase("X", "10.00", 1, "1999-01-01", f.food()),
                purchase("", "10.00", 1, "2026-11-01", f.food()),
                purchase("X\u0000", "10.00", 1, "2026-11-01", f.food()),
                purchase("X", "10.00", 1, "2026-11-01", f.salary()));
        for (Map<String, Object> b : invalid) {
            assertThat(buy(f, b).getStatus()).as(b.toString()).isEqualTo(400);
        }
        Map<String, Object> noCategory = purchase("X", "10.00", 1, "2026-11-01", null);
        noCategory.remove("categoryId");
        assertThat(buy(f, noCategory).getStatus()).isEqualTo(400);
        assertThat(statement(f, "2026-11").get("total").decimalValue()).as("nada gravado").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Limite disponível: desce com as compras (todas as parcelas) e volta com o pagamento")
    void limit() throws Exception {
        Fx f = fixture();
        buy(f, purchase("TV", "1200.00", 12, "2026-11-05", f.food()));
        assertThat(available(f)).isEqualByComparingTo("3800.00");
        call(post(ws(f.s(), "/cards/" + f.card() + "/statements/2026-11/payments")), f.s().token(),
                Map.of("accountId", f.account(), "amount", "100.00", "date", "2026-11-17"));
        assertThat(available(f)).isEqualByComparingTo("3900.00");
    }

    @Test
    @DisplayName("Pagar a fatura: sai da conta, marca a fatura como paga e NÃO conta como gasto (o gasto já está nas parcelas)")
    void payment() throws Exception {
        Fx f = fixture();
        buy(f, purchase("Mercado", "250.00", 1, "2026-11-03", f.food()));
        MockHttpServletResponse partial = call(post(ws(f.s(), "/cards/" + f.card() + "/statements/2026-11/payments")), f.s().token(),
                Map.of("accountId", f.account(), "amount", "100.00", "date", "2026-11-15"));
        assertThat(partial.getStatus()).isEqualTo(201);
        assertThat(body(partial).get("type").asText()).isEqualTo("CARD_PAYMENT");
        JsonNode st = statement(f, "2026-11");
        assertThat(st.get("paid").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(st.get("remaining").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(st.get("status").asText()).isIn("OPEN", "CLOSED");

        call(post(ws(f.s(), "/cards/" + f.card() + "/statements/2026-11/payments")), f.s().token(),
                Map.of("accountId", f.account(), "amount", "150.00", "date", "2026-11-16"));
        st = statement(f, "2026-11");
        assertThat(st.get("status").asText()).isEqualTo("PAID");
        assertThat(st.get("payments")).hasSize(2);

        JsonNode acc = body(call(get(ws(f.s(), "/accounts/" + f.account())), f.s().token(), null));
        assertThat(acc.get("balance").decimalValue()).isEqualByComparingTo("2750.00");
        JsonNode d = body(call(get(ws(f.s(), "/dashboard?month=2026-11")), f.s().token(), null));
        assertThat(d.get("expense").decimalValue()).as("compra de 250 conta; pagamento de 250 não").isEqualByComparingTo("250.00");

        JsonNode list = body(call(get(ws(f.s(), "/transactions?month=2026-11&type=CARD_PAYMENT")), f.s().token(), null));
        assertThat(list.get("total").asInt()).isEqualTo(2);
        assertThat(list.get("items").get(0).get("card").get("name").asText()).isEqualTo("Nubank");
    }

    @Test
    @DisplayName("Pagamento: valor > 0, conta ativa do espaço, mês válido; excluir o pagamento reabre a fatura")
    void paymentRulesAndUndo() throws Exception {
        Fx f = fixture();
        buy(f, purchase("Mercado", "50.00", 1, "2026-11-03", f.food()));
        String path = ws(f.s(), "/cards/" + f.card() + "/statements/2026-11/payments");
        assertThat(call(post(path), f.s().token(), Map.of("accountId", f.account(), "amount", "0", "date", "2026-11-15")).getStatus()).isEqualTo(400);
        assertThat(call(post(ws(f.s(), "/cards/" + f.card() + "/statements/2026-13/payments")), f.s().token(),
                Map.of("accountId", f.account(), "amount", "1.00", "date", "2026-11-15")).getStatus()).isEqualTo(400);
        Fx other = fixture();
        MockHttpServletResponse foreign = call(post(path), f.s().token(), Map.of("accountId", other.account(), "amount", "1.00", "date", "2026-11-15"));
        assertThat(body(foreign).get("message").asText()).isEqualTo("Account not found");

        String tx = body(call(post(path), f.s().token(), Map.of("accountId", f.account(), "amount", "50.00", "date", "2026-11-15"))).get("id").asText();
        assertThat(statement(f, "2026-11").get("status").asText()).isEqualTo("PAID");
        assertThat(call(delete(ws(f.s(), "/transactions/" + tx)), f.s().token(), null).getStatus()).isEqualTo(204);
        assertThat(statement(f, "2026-11").get("paid").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Pagamento de fatura não se cria nem se edita pela rota genérica de lançamentos")
    void cardPaymentOnlyThroughStatement() throws Exception {
        Fx f = fixture();
        // completo para a rota genérica (com categoria): a recusa tem de ser pela regra do pagamento, não por outro campo
        Map<String, Object> generic = new HashMap<>(Map.of("type", "CARD_PAYMENT", "amount", "10.00", "date", "2026-11-15",
                "description", "Fatura", "accountId", f.account(), "categoryId", f.food()));
        MockHttpServletResponse created = call(post(ws(f.s(), "/transactions")), f.s().token(), generic);
        assertThat(created.getStatus()).isEqualTo(400);
        assertThat(body(created).get("message").asText()).isEqualTo("Card payments are made through the card statement");

        buy(f, purchase("Mercado", "50.00", 1, "2026-11-03", f.food()));
        String tx = body(call(post(ws(f.s(), "/cards/" + f.card() + "/statements/2026-11/payments")), f.s().token(),
                Map.of("accountId", f.account(), "amount", "50.00", "date", "2026-11-15"))).get("id").asText();
        MockHttpServletResponse edit = call(put(ws(f.s(), "/transactions/" + tx)), f.s().token(),
                Map.of("type", "EXPENSE", "amount", "1.00", "date", "2026-11-15", "description", "Virou despesa",
                        "accountId", f.account(), "categoryId", f.food()));
        assertThat(edit.getStatus()).isEqualTo(400);
        assertThat(body(edit).get("message").asText()).isEqualTo("Card payments are changed through the card statement");
    }

    @Test
    @DisplayName("Status: fatura futura ABERTA, passada sem pagamento FECHADA")
    void status() throws Exception {
        Fx f = fixture();
        YearMonth thisMonth = YearMonth.now(ZoneId.of("America/Sao_Paulo"));
        String future = thisMonth.plusMonths(2).toString();
        String past = thisMonth.minusMonths(2).toString();
        buy(f, purchase("Futura", "10.00", 1, thisMonth.plusMonths(2).atDay(1).toString(), f.food()));
        buy(f, purchase("Passada", "10.00", 1, thisMonth.minusMonths(2).atDay(1).toString(), f.food()));
        assertThat(statement(f, future).get("status").asText()).isEqualTo("OPEN");
        assertThat(statement(f, past).get("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Painel: o gasto no cartão entra no mês da fatura e na categoria; o painel lista as faturas do mês")
    void dashboard() throws Exception {
        Fx f = fixture();
        buy(f, purchase("Restaurante", "90.00", 1, "2026-11-08", f.food()));
        buy(f, purchase("Celular", "600.00", 3, "2026-10-20", f.food()));     // fatura de nov: 200
        call(post(ws(f.s(), "/transactions")), f.s().token(), Map.of("type", "EXPENSE", "amount", "10.00", "date", "2026-11-02",
                "description", "Café", "accountId", f.account(), "categoryId", f.food()));
        JsonNode d = body(call(get(ws(f.s(), "/dashboard?month=2026-11")), f.s().token(), null));
        assertThat(d.get("expense").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(d.get("expensesByCategory").get(0).get("name").asText()).isEqualTo("Alimentação");
        assertThat(d.get("expensesByCategory").get(0).get("amount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(d.get("cards")).hasSize(1);
        assertThat(d.get("cards").get(0).get("statementTotal").decimalValue()).isEqualByComparingTo("290.00");
        assertThat(d.get("cards").get(0).get("dueDate").asText()).isEqualTo("2026-11-17");
    }

    @Test
    @DisplayName("Editar compra refaz as parcelas; excluir tira das faturas; cartão com compras só arquiva")
    void editDeleteArchive() throws Exception {
        Fx f = fixture();
        String p = body(buy(f, purchase("Sofá", "300.00", 3, "2026-11-05", f.food()))).get("id").asText();
        MockHttpServletResponse edit = call(put(ws(f.s(), "/cards/" + f.card() + "/purchases/" + p)), f.s().token(),
                purchase("Sofá (2×)", "300.00", 2, "2026-11-05", f.food()));
        assertThat(edit.getStatus()).isEqualTo(200);
        assertThat(statement(f, "2026-11").get("total").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(statement(f, "2027-01").get("total").decimalValue()).isEqualByComparingTo("0");

        MockHttpServletResponse del = call(delete(ws(f.s(), "/cards/" + f.card())), f.s().token(), null);
        assertThat(del.getStatus()).isEqualTo(409);
        assertThat(body(del).get("message").asText()).isEqualTo("Card has purchases; archive it instead");

        assertThat(call(delete(ws(f.s(), "/cards/" + f.card() + "/purchases/" + p)), f.s().token(), null).getStatus()).isEqualTo(204);
        assertThat(statement(f, "2026-11").get("items")).isEmpty();
        assertThat(call(delete(ws(f.s(), "/cards/" + f.card())), f.s().token(), null).getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("Cartão arquivado não recebe compra nova; editar cartão")
    void archivedCard() throws Exception {
        Fx f = fixture();
        Map<String, Object> upd = card("Nubank Ultravioleta", "1234", 3, 10, "8000.00");
        upd.put("archived", true);
        MockHttpServletResponse r = call(put(ws(f.s(), "/cards/" + f.card())), f.s().token(), upd);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r).get("closingDay").asInt()).isEqualTo(3);
        MockHttpServletResponse b = buy(f, purchase("X", "10.00", 1, "2026-11-01", f.food()));
        assertThat(b.getStatus()).isEqualTo(400);
        assertThat(body(b).get("message").asText()).isEqualTo("Card is archived");
    }

    @Test
    @DisplayName("Isolamento: cartão, compra e fatura de outro espaço são inacessíveis; nada da vítima muda")
    void isolation() throws Exception {
        Fx victim = fixture();
        String p = body(buy(victim, purchase("Segredo", "77.00", 1, "2026-11-05", victim.food()))).get("id").asText();
        Fx eve = fixture();
        String vc = "/cards/" + victim.card();
        for (MockHttpServletResponse r : new MockHttpServletResponse[]{
                call(get(ws(victim.s(), vc)), eve.s().token(), null),
                call(get(ws(victim.s(), vc + "/statements/2026-11")), eve.s().token(), null),
                call(post(ws(victim.s(), vc + "/purchases")), eve.s().token(), purchase("X", "1.00", 1, "2026-11-01", eve.food())),
                call(delete(ws(victim.s(), vc + "/purchases/" + p)), eve.s().token(), null),
                call(get(ws(eve.s(), vc)), eve.s().token(), null),
                call(get(ws(eve.s(), vc + "/statements/2026-11")), eve.s().token(), null),
                call(post(ws(eve.s(), vc + "/purchases")), eve.s().token(), purchase("X", "1.00", 1, "2026-11-01", eve.food())),
                call(put(ws(eve.s(), vc + "/purchases/" + p)), eve.s().token(), purchase("X", "1.00", 1, "2026-11-01", eve.food())),
                call(delete(ws(eve.s(), vc + "/purchases/" + p)), eve.s().token(), null),
                call(delete(ws(eve.s(), "/cards/" + eve.card() + "/purchases/" + p)), eve.s().token(), null),
                call(post(ws(eve.s(), vc + "/statements/2026-11/payments")), eve.s().token(),
                        Map.of("accountId", eve.account(), "amount", "1.00", "date", "2026-11-15"))}) {
            assertThat(r.getStatus()).isEqualTo(404);
            assertThat(r.getContentAsString()).doesNotContain("Segredo");
        }
        MockHttpServletResponse foreignCategory = buy(eve, purchase("X", "1.00", 1, "2026-11-01", victim.food()));
        assertThat(body(foreignCategory).get("message").asText()).isEqualTo("Category not found");
        assertThat(statement(victim, "2026-11").get("items").findValuesAsText("description")).containsExactly("Segredo");
    }

    @Test
    @DisplayName("Regras de edição: nome repetido, categoria arquivada, pagamento sem compra, cartão arquivado fora do painel")
    void editingRules() throws Exception {
        Fx f = fixture();
        call(post(ws(f.s(), "/cards")), f.s().token(), card("Inter", null, 5, 12, "100.00"));
        assertThat(call(put(ws(f.s(), "/cards/" + f.card())), f.s().token(), card("INTER", "1234", 10, 17, "5000.00")).getStatus())
                .as("nome de outro cartão").isEqualTo(409);
        assertThat(call(put(ws(f.s(), "/cards/" + f.card())), f.s().token(), card("NUBANK", "1234", 10, 17, "5000.00")).getStatus())
                .as("só maiúsculas do próprio").isEqualTo(200);

        String p = body(buy(f, purchase("Jantar", "80.00", 1, "2026-11-05", f.food()))).get("id").asText();
        call(put(ws(f.s(), "/categories/" + f.food())), f.s().token(), Map.of("name", "Alimentação", "color", "#f97316", "archived", true));
        assertThat(body(buy(f, purchase("Outro", "10.00", 1, "2026-11-05", f.food()))).get("message").asText()).isEqualTo("Category is archived");
        assertThat(call(put(ws(f.s(), "/cards/" + f.card() + "/purchases/" + p)), f.s().token(),
                purchase("Jantar (corrigido)", "85.00", 1, "2026-11-05", f.food())).getStatus()).as("mantém a categoria que já era dela").isEqualTo(200);

        String pay = ws(f.s(), "/cards/" + f.card() + "/statements/2026-11/payments");
        call(post(pay), f.s().token(), Map.of("accountId", f.account(), "amount", "85.00", "date", "2026-11-15"));
        call(delete(ws(f.s(), "/cards/" + f.card() + "/purchases/" + p)), f.s().token(), null);
        assertThat(call(delete(ws(f.s(), "/cards/" + f.card())), f.s().token(), null).getStatus()).as("ainda há pagamento").isEqualTo(409);
        assertThat(statement(f, "2026-11").get("status").asText()).as("sem compras, nada a pagar").isIn("OPEN", "CLOSED");

        Map<String, Object> archived = card("Nubank", "1234", 10, 17, "5000.00");
        archived.put("archived", true);
        call(put(ws(f.s(), "/cards/" + f.card())), f.s().token(), archived);
        JsonNode d = body(call(get(ws(f.s(), "/dashboard?month=2026-11")), f.s().token(), null));
        assertThat(d.get("cards").findValuesAsText("name")).containsExactly("Inter");
        assertThat(call(get(ws(f.s(), "/cards")), f.s().token(), null).getStatus()).isEqualTo(200);
        assertThat(body(call(get(ws(f.s(), "/cards")), f.s().token(), null))).hasSize(2);
        assertThat(call(get(ws(f.s(), "/cards/" + f.card() + "/purchases/" + p)), f.s().token(), null).getStatus()).isIn(404, 405);
    }
}
