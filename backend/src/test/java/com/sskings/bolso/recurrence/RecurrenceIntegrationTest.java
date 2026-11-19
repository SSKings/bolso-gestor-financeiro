package com.sskings.bolso.recurrence;

import tools.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Contas fixas e assinaturas. "Hoje" é 10/11/2026 (meio-dia em São Paulo). */
class RecurrenceIntegrationTest extends IntegrationTest {

    private record Fx(Session s, String account, String rent, String salary, String fun) {}

    @BeforeEach
    void today() {
        clock.set(Instant.parse("2026-11-10T15:00:00Z"));
    }

    private Fx fixture() throws Exception {
        Session s = register();
        String account = body(call(post(ws(s, "/accounts")), s.token(),
                Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "1000.00"))).get("id").asString();
        String rent = null, salary = null, fun = null;
        for (JsonNode c : body(call(get(ws(s, "/categories")), s.token(), null))) {
            switch (c.get("name").asString() + "/" + c.get("kind").asString()) {
                case "Moradia/EXPENSE" -> rent = c.get("id").asString();
                case "Salário/INCOME" -> salary = c.get("id").asString();
                case "Lazer/EXPENSE" -> fun = c.get("id").asString();
                default -> { }
            }
        }
        return new Fx(s, account, rent, salary, fun);
    }

    private static Map<String, Object> rec(String type, String desc, String amount, int day, String category, String account, String start, String end) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("description", desc);
        m.put("amount", amount);
        m.put("dayOfMonth", day);
        m.put("categoryId", category);
        m.put("accountId", account);
        m.put("startMonth", start);
        if (end != null) m.put("endMonth", end);
        return m;
    }

    private String create(Fx f, Map<String, Object> body) throws Exception {
        MockHttpServletResponse r = call(post(ws(f.s(), "/recurrences")), f.s().token(), body);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return body(r).get("id").asString();
    }

    private JsonNode occurrences(Fx f, String month) throws Exception {
        return body(call(get(ws(f.s(), "/recurrences/occurrences?month=" + month)), f.s().token(), null));
    }

    private MockHttpServletResponse confirm(Fx f, String id, String month, String amount, String date) throws Exception {
        return call(post(ws(f.s(), "/recurrences/" + id + "/occurrences/" + month + "/confirm")), f.s().token(),
                Map.of("amount", amount, "date", date, "accountId", f.account()));
    }

    @Test
    @DisplayName("Ocorrências do mês: atrasada, vence em breve (7 dias), pendente e paga — com o vencimento de cada uma")
    void occurrencesStatus() throws Exception {
        Fx f = fixture();
        create(f, rec("EXPENSE", "Internet", "100.00", 5, f.rent(), f.account(), "2026-01", null));
        create(f, rec("EXPENSE", "Aluguel", "1500.00", 15, f.rent(), f.account(), "2026-01", null));
        create(f, rec("INCOME", "Salário", "3000.00", 25, f.salary(), f.account(), "2026-01", null));
        String streaming = create(f, rec("EXPENSE", "Streaming", "40.00", 8, f.fun(), f.account(), "2026-01", null));
        assertThat(confirm(f, streaming, "2026-11", "39.90", "2026-11-08").getStatus()).isEqualTo(201);

        JsonNode list = occurrences(f, "2026-11");
        Map<String, String> status = new HashMap<>();
        for (JsonNode o : list) status.put(o.get("description").asString(), o.get("status").asString());
        assertThat(status).containsEntry("Internet", "OVERDUE").containsEntry("Aluguel", "DUE_SOON")
                .containsEntry("Salário", "PENDING").containsEntry("Streaming", "PAID");
        JsonNode paid = null;
        for (JsonNode o : list) if (o.get("description").asString().equals("Streaming")) paid = o;
        assertThat(paid.get("paidAmount").decimalValue()).isEqualByComparingTo("39.90");
        assertThat(paid.get("expectedAmount").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(list.get(0).get("dueDate").asString()).as("ordenadas pelo vencimento").isEqualTo("2026-11-05");
    }

    @Test
    @DisplayName("Confirmar cria o lançamento com o valor real; não confirma duas vezes no mesmo mês")
    void confirm() throws Exception {
        Fx f = fixture();
        String id = create(f, rec("EXPENSE", "Luz", "180.00", 12, f.rent(), f.account(), "2026-01", null));
        MockHttpServletResponse r = confirm(f, id, "2026-11", "203.47", "2026-11-12");
        assertThat(r.getStatus()).isEqualTo(201);
        JsonNode tx = body(r);
        assertThat(tx.get("type").asString()).isEqualTo("EXPENSE");
        assertThat(tx.get("amount").decimalValue()).isEqualByComparingTo("203.47");
        assertThat(tx.get("description").asString()).isEqualTo("Luz");
        assertThat(tx.get("category").get("id").asString()).isEqualTo(f.rent());
        assertThat(tx.get("recurrence").get("id").asString()).isEqualTo(id);

        MockHttpServletResponse again = confirm(f, id, "2026-11", "1.00", "2026-11-12");
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(body(again).get("message").asString()).isEqualTo("Already confirmed for this month");
        assertThat(confirm(f, id, "2026-12", "180.00", "2026-12-12").getStatus()).as("outro mês pode").isEqualTo(201);
    }

    @Test
    @DisplayName("Confirmações simultâneas do mesmo mês: exatamente uma vale")
    void concurrentConfirm() throws Exception {
        Fx f = fixture();
        String id = create(f, rec("EXPENSE", "Condomínio", "600.00", 10, f.rent(), f.account(), "2026-01", null));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Integer> codes = new ArrayList<>();
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> confirm(f, id, "2026-11", "600.00", "2026-11-10").getStatus()));
            for (Future<Integer> fu : futures) codes.add(fu.get());
        } finally {
            pool.shutdownNow();
        }
        assertThat(codes.stream().filter(c -> c == 201).count()).isEqualTo(1);
        assertThat(codes).allMatch(c -> c == 201 || c == 409);
        assertThat(body(call(get(ws(f.s(), "/accounts/" + f.account())), f.s().token(), null)).get("balance").decimalValue())
                .isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Período: antes do início, depois do fim ou pausada não aparece nem confirma")
    void schedule() throws Exception {
        Fx f = fixture();
        create(f, rec("EXPENSE", "Curso", "300.00", 10, f.rent(), f.account(), "2026-03", "2026-09"));
        create(f, rec("EXPENSE", "Seguro", "90.00", 20, f.rent(), f.account(), "2026-12", null));
        String paused = create(f, rec("EXPENSE", "Academia", "120.00", 3, f.fun(), f.account(), "2026-01", null));
        Map<String, Object> pause = rec("EXPENSE", "Academia", "120.00", 3, f.fun(), f.account(), "2026-01", null);
        pause.put("active", false);
        assertThat(call(put(ws(f.s(), "/recurrences/" + paused)), f.s().token(), pause).getStatus()).isEqualTo(200);

        assertThat(occurrences(f, "2026-11").findValuesAsString("description")).isEmpty();
        assertThat(occurrences(f, "2026-12").findValuesAsString("description")).containsExactly("Seguro");
        assertThat(occurrences(f, "2026-05").findValuesAsString("description")).as("Academia pausada não aparece").containsExactly("Curso");

        MockHttpServletResponse outside = confirm(f, recId(f, "Curso"), "2026-11", "300.00", "2026-11-10");
        assertThat(body(outside).get("message").asString()).isEqualTo("Not scheduled for this month");
        MockHttpServletResponse pausedConfirm = confirm(f, paused, "2026-11", "120.00", "2026-11-03");
        assertThat(body(pausedConfirm).get("message").asString()).isEqualTo("Recurrence is paused");
    }

    private String recId(Fx f, String description) throws Exception {
        for (JsonNode r : body(call(get(ws(f.s(), "/recurrences")), f.s().token(), null))) {
            if (r.get("description").asString().equals(description)) return r.get("id").asString();
        }
        throw new AssertionError("não achei " + description);
    }

    @Test
    @DisplayName("Validação: dia 1–28, valor, tipo × categoria, fim antes do início, texto, conta arquivada")
    void validation() throws Exception {
        Fx f = fixture();
        List<Map<String, Object>> invalid = List.of(
                rec("EXPENSE", "X", "10.00", 0, f.rent(), f.account(), "2026-01", null),
                rec("EXPENSE", "X", "10.00", 29, f.rent(), f.account(), "2026-01", null),
                rec("EXPENSE", "X", "0", 5, f.rent(), f.account(), "2026-01", null),
                rec("EXPENSE", "X", "10.001", 5, f.rent(), f.account(), "2026-01", null),
                rec("TRANSFER", "X", "10.00", 5, f.rent(), f.account(), "2026-01", null),
                rec("CARD_PAYMENT", "X", "10.00", 5, f.rent(), f.account(), "2026-01", null),
                rec("INCOME", "X", "10.00", 5, f.rent(), f.account(), "2026-01", null),
                rec("EXPENSE", "X", "10.00", 5, f.rent(), f.account(), "2026-05", "2026-04"),
                rec("EXPENSE", "", "10.00", 5, f.rent(), f.account(), "2026-01", null),
                rec("EXPENSE", "X\nY", "10.00", 5, f.rent(), f.account(), "2026-01", null),
                rec("EXPENSE", "X", "10.00", 5, f.rent(), f.account(), "1999-01", null));
        for (Map<String, Object> b : invalid) {
            assertThat(call(post(ws(f.s(), "/recurrences")), f.s().token(), b).getStatus()).as(b.toString()).isEqualTo(400);
        }
        call(put(ws(f.s(), "/accounts/" + f.account())), f.s().token(),
                Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "1000.00", "archived", true));
        MockHttpServletResponse archived = call(post(ws(f.s(), "/recurrences")), f.s().token(),
                rec("EXPENSE", "X", "10.00", 5, f.rent(), f.account(), "2026-01", null));
        assertThat(body(archived).get("message").asString()).isEqualTo("Account is archived");
    }

    @Test
    @DisplayName("Excluir a conta fixa mantém os lançamentos já confirmados; excluir o lançamento reabre o mês")
    void deleteKeepsHistory() throws Exception {
        Fx f = fixture();
        String id = create(f, rec("EXPENSE", "Água", "80.00", 10, f.rent(), f.account(), "2026-01", null));
        String tx = body(confirm(f, id, "2026-10", "75.00", "2026-10-10")).get("id").asString();
        String txNov = body(confirm(f, id, "2026-11", "82.00", "2026-11-10")).get("id").asString();

        assertThat(call(delete(ws(f.s(), "/transactions/" + txNov)), f.s().token(), null).getStatus()).isEqualTo(204);
        assertThat(occurrences(f, "2026-11").get(0).get("status").asString()).isIn("DUE_SOON", "OVERDUE", "PENDING");

        assertThat(call(delete(ws(f.s(), "/recurrences/" + id)), f.s().token(), null).getStatus()).isEqualTo(204);
        JsonNode kept = body(call(get(ws(f.s(), "/transactions/" + tx)), f.s().token(), null));
        assertThat(kept.get("amount").decimalValue()).isEqualByComparingTo("75.00");
        assertThat(kept.has("recurrence")).isFalse();
    }

    @Test
    @DisplayName("Lançamento de conta fixa: pode corrigir valor, mas não virar transferência; categoria em uso não é excluída")
    void linkedTransactionRules() throws Exception {
        Fx f = fixture();
        String id = create(f, rec("EXPENSE", "Gás", "60.00", 10, f.rent(), f.account(), "2026-01", null));
        String tx = body(confirm(f, id, "2026-11", "60.00", "2026-11-10")).get("id").asString();
        assertThat(call(put(ws(f.s(), "/transactions/" + tx)), f.s().token(), Map.of("type", "EXPENSE", "amount", "65.00",
                "date", "2026-11-10", "description", "Gás", "accountId", f.account(), "categoryId", f.rent())).getStatus()).isEqualTo(200);
        String other = body(call(post(ws(f.s(), "/accounts")), f.s().token(), Map.of("name", "Poupança", "type", "SAVINGS", "initialBalance", "0"))).get("id").asString();
        MockHttpServletResponse transfer = call(put(ws(f.s(), "/transactions/" + tx)), f.s().token(), Map.of("type", "TRANSFER", "amount", "65.00",
                "date", "2026-11-10", "description", "Gás", "accountId", f.account(), "toAccountId", other));
        assertThat(transfer.getStatus()).isEqualTo(400);
        assertThat(body(transfer).get("message").asString()).isEqualTo("Bill transactions keep their type");

        MockHttpServletResponse catDelete = call(delete(ws(f.s(), "/categories/" + f.fun())), f.s().token(), null);
        assertThat(catDelete.getStatus()).as("Lazer não tem uso").isEqualTo(204);
        create(f, rec("EXPENSE", "Cinema", "30.00", 10, f.rent(), f.account(), "2026-01", null));
        assertThat(call(delete(ws(f.s(), "/categories/" + f.rent())), f.s().token(), null).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Isolamento: conta fixa de outro espaço é inexistente por todas as rotas")
    void isolation() throws Exception {
        Fx victim = fixture();
        String id = create(victim, rec("EXPENSE", "Segredo", "10.00", 10, victim.rent(), victim.account(), "2026-01", null));
        Fx eve = fixture();
        for (MockHttpServletResponse r : new MockHttpServletResponse[]{
                call(get(ws(victim.s(), "/recurrences")), eve.s().token(), null),
                call(get(ws(victim.s(), "/recurrences/occurrences?month=2026-11")), eve.s().token(), null),
                call(put(ws(eve.s(), "/recurrences/" + id)), eve.s().token(), rec("EXPENSE", "X", "1.00", 1, eve.rent(), eve.account(), "2026-01", null)),
                call(delete(ws(eve.s(), "/recurrences/" + id)), eve.s().token(), null),
                call(post(ws(eve.s(), "/recurrences/" + id + "/occurrences/2026-11/confirm")), eve.s().token(),
                        Map.of("amount", "1.00", "date", "2026-11-10", "accountId", eve.account()))}) {
            assertThat(r.getStatus()).isEqualTo(404);
            assertThat(r.getContentAsString()).doesNotContain("Segredo");
        }
        MockHttpServletResponse foreign = call(post(ws(eve.s(), "/recurrences")), eve.s().token(),
                rec("EXPENSE", "X", "1.00", 1, victim.rent(), eve.account(), "2026-01", null));
        assertThat(body(foreign).get("message").asString()).isEqualTo("Category not found");
        MockHttpServletResponse foreignAccount = call(post(ws(victim.s(), "/recurrences/" + id + "/occurrences/2026-11/confirm")), victim.s().token(),
                Map.of("amount", "1.00", "date", "2026-11-10", "accountId", eve.account()));
        assertThat(body(foreignAccount).get("message").asString()).isEqualTo("Account not found");
        assertThat(occurrences(victim, "2026-11").get(0).get("status").asString()).isNotEqualTo("PAID");
    }

    @Test
    @DisplayName("Editar com categoria/conta arquivadas DEPOIS: permitido; trocar PARA arquivada: recusado; pausada e já paga segue PAGA")
    void editAndPaused() throws Exception {
        Fx f = fixture();
        String id = create(f, rec("EXPENSE", "Plano", "50.00", 9, f.fun(), f.account(), "2026-01", null));
        assertThat(confirm(f, id, "2026-11", "50.00", "2026-11-09").getStatus()).isEqualTo(201);
        call(put(ws(f.s(), "/categories/" + f.fun())), f.s().token(), Map.of("name", "Lazer", "color", "#ec4899", "archived", true));
        call(put(ws(f.s(), "/accounts/" + f.account())), f.s().token(),
                Map.of("name", "Corrente", "type", "CHECKING", "initialBalance", "1000.00", "archived", true));

        Map<String, Object> pause = rec("EXPENSE", "Plano", "55.00", 9, f.fun(), f.account(), "2026-01", null);
        pause.put("active", false);
        assertThat(call(put(ws(f.s(), "/recurrences/" + id)), f.s().token(), pause).getStatus()).as("mantém as arquivadas").isEqualTo(200);
        assertThat(occurrences(f, "2026-11").findValuesAsString("status")).as("pausada, mas paga no mês").containsExactly("PAID");
        assertThat(occurrences(f, "2026-12")).isEmpty();

        String other = body(call(post(ws(f.s(), "/accounts")), f.s().token(), Map.of("name", "Nova", "type", "CHECKING", "initialBalance", "0"))).get("id").asString();
        MockHttpServletResponse toActive = call(put(ws(f.s(), "/recurrences/" + id)), f.s().token(),
                rec("EXPENSE", "Plano", "55.00", 9, f.rent(), other, "2026-01", null));
        assertThat(toActive.getStatus()).as("trocar para categoria ativa e conta ativa").isEqualTo(200);
        MockHttpServletResponse backToArchived = call(put(ws(f.s(), "/recurrences/" + id)), f.s().token(),
                rec("EXPENSE", "Plano", "55.00", 9, f.fun(), other, "2026-01", null));
        assertThat(body(backToArchived).get("message").asString()).isEqualTo("Category is archived");
        MockHttpServletResponse backToArchivedAccount = call(put(ws(f.s(), "/recurrences/" + id)), f.s().token(),
                rec("EXPENSE", "Plano", "55.00", 9, f.rent(), f.account(), "2026-01", null));
        assertThat(body(backToArchivedAccount).get("message").asString()).isEqualTo("Account is archived");
        assertThat(call(get(ws(f.s(), "/recurrences")), f.s().token(), null).getStatus()).isEqualTo(200);
        assertThat(call(delete(ws(f.s(), "/accounts/" + other)), f.s().token(), null).getStatus())
                .as("conta usada por conta fixa não é excluída").isEqualTo(409);
    }
}
