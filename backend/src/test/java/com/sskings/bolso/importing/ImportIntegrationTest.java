package com.sskings.bolso.importing;

import tools.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Importação de extrato: prévia → revisão → importação, sem duplicar. */
class ImportIntegrationTest extends IntegrationTest {

    private record Fx(Session s, String account, Map<String, String> cats) {}

    private Fx fixture() throws Exception {
        Session s = register();
        String account = body(call(post(ws(s, "/accounts")), s.token(),
                Map.of("name", "Itaú", "type", "CHECKING", "initialBalance", "100.00"))).get("id").asString();
        Map<String, String> cats = new HashMap<>();
        for (JsonNode c : body(call(get(ws(s, "/categories")), s.token(), null))) cats.put(c.get("name").asString() + "/" + c.get("kind").asString(), c.get("id").asString());
        return new Fx(s, account, cats);
    }

    private static byte[] sample(String name) throws Exception {
        try (InputStream in = ImportIntegrationTest.class.getResourceAsStream("/statements/" + name)) {
            return in.readAllBytes();
        }
    }

    private MockHttpServletResponse preview(Session s, String wsPath, String account, String name, byte[] content) throws Exception {
        return call(multipart(wsPath + "/imports/preview").file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .param("accountId", account), s.token(), null);
    }

    private JsonNode preview(Fx f, String name) throws Exception {
        MockHttpServletResponse r = preview(f.s(), ws(f.s(), ""), f.account(), name, sample(name));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    /** Monta o pedido de importação a partir da prévia, com a categoria sugerida (como o front faz). */
    private Map<String, Object> commitBody(Fx f, JsonNode preview) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (JsonNode e : preview.get("entries")) {
            if (e.get("duplicate").asBoolean()) continue;
            entries.add(Map.of("externalId", e.get("externalId").asString(), "date", e.get("date").asString(), "amount", e.get("amount").asString(),
                    "type", e.get("type").asString(), "description", e.get("description").asString(),
                    "categoryId", e.get("suggestedCategory").get("id").asString()));
        }
        return Map.of("accountId", f.account(), "entries", entries);
    }

    private MockHttpServletResponse commit(Fx f, Object body) throws Exception {
        return call(post(ws(f.s(), "/imports/commit")), f.s().token(), body);
    }

    private String balance(Fx f) throws Exception {
        return body(call(get(ws(f.s(), "/accounts/" + f.account())), f.s().token(), null)).get("balance").decimalValue().setScale(2).toPlainString();
    }

    @Test
    @DisplayName("OFX: prévia com tipo pelo sinal e categoria pelas regras (ou 'Outros'); importa; reimportar não duplica")
    void ofxFlow() throws Exception {
        Fx f = fixture();
        call(post(ws(f.s(), "/rules")), f.s().token(), Map.of("pattern", "uber", "categoryId", f.cats().get("Transporte/EXPENSE")));
        JsonNode p = preview(f, "extrato.ofx");
        assertThat(p.get("entries")).hasSize(3);
        JsonNode uber = p.get("entries").get(2);
        assertThat(uber.get("type").asString()).isEqualTo("EXPENSE");
        assertThat(uber.get("amount").decimalValue()).isEqualByComparingTo("23.10");
        assertThat(uber.get("suggestedCategory").get("name").asString()).isEqualTo("Transporte");
        assertThat(p.get("entries").get(1).get("type").asString()).isEqualTo("INCOME");
        assertThat(p.get("entries").get(1).get("suggestedCategory").get("id").asString()).as("sem regra: Outros (receita)")
                .isEqualTo(f.cats().get("Outros/INCOME"));
        assertThat(p.get("entries").get(0).get("duplicate").asBoolean()).isFalse();

        MockHttpServletResponse r = commit(f, commitBody(f, p));
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r).get("imported").asInt()).isEqualTo(3);
        assertThat(balance(f)).isEqualTo("3541.00");                     // 100 − 35,90 + 3500 − 23,10

        JsonNode again = preview(f, "extrato.ofx");
        assertThat(again.get("entries").findValuesAsString("duplicate")).containsOnly("true");
        Map<String, Object> forced = new HashMap<>(commitBody(f, p));                 // mandando de novo, de propósito
        JsonNode second = body(commit(f, forced));
        assertThat(second.get("imported").asInt()).isZero();
        assertThat(second.get("skipped").asInt()).isEqualTo(3);
        assertThat(balance(f)).isEqualTo("3541.00");

        JsonNode list = body(call(get(ws(f.s(), "/transactions?month=2026-11")), f.s().token(), null));
        assertThat(list.get("items").get(0).get("source").asString()).isEqualTo("IMPORT");
    }

    @Test
    @DisplayName("Lançamento manual com mesma data e valor aparece como 'parecido' (pode ser o mesmo gasto)")
    void similar() throws Exception {
        Fx f = fixture();
        call(post(ws(f.s(), "/transactions")), f.s().token(), Map.of("type", "EXPENSE", "amount", "35.90", "date", "2026-11-03",
                "description", "Pão", "accountId", f.account(), "categoryId", f.cats().get("Alimentação/EXPENSE")));
        JsonNode p = preview(f, "nubank-conta.csv");
        assertThat(p.get("entries").get(0).get("similar").asBoolean()).isTrue();
        assertThat(p.get("entries").get(1).get("similar").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Arquivo: formato desconhecido → 400; acima de 1 MB → 413; outras rotas continuam limitadas a 64 KB")
    void fileLimits() throws Exception {
        Fx f = fixture();
        MockHttpServletResponse bad = preview(f.s(), ws(f.s(), ""), f.account(), "foto.jpg", "ÿØ não é extrato".getBytes());
        assertThat(bad.getStatus()).isEqualTo(400);
        assertThat(body(bad).get("message").asString()).isEqualTo("Unsupported file format");
        byte[] big = new byte[1_100_000];
        java.util.Arrays.fill(big, (byte) 'a');
        assertThat(preview(f.s(), ws(f.s(), ""), f.account(), "grande.csv", big).getStatus()).isEqualTo(413);
        assertThat(call(post(ws(f.s(), "/accounts")), f.s().token(), "{\"name\":\"" + "a".repeat(70_000) + "\"}").getStatus()).isEqualTo(413);
        MockHttpServletResponse noFile = call(multipart(ws(f.s(), "/imports/preview")).param("accountId", f.account()), f.s().token(), null);
        assertThat(noFile.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Importação valida tudo antes de gravar: um item ruim recusa o lote inteiro")
    void commitValidation() throws Exception {
        Fx f = fixture();
        String food = f.cats().get("Alimentação/EXPENSE");
        List<Map<String, Object>> bad = List.of(
                Map.of("externalId", "x1", "date", "2026-11-03", "amount", "0", "type", "EXPENSE", "description", "X", "categoryId", food),
                Map.of("externalId", "x1", "date", "2026-11-03", "amount", "1.00", "type", "TRANSFER", "description", "X", "categoryId", food),
                Map.of("externalId", "x1", "date", "2026-11-03", "amount", "1.00", "type", "INCOME", "description", "X", "categoryId", food),
                Map.of("externalId", "x".repeat(81), "date", "2026-11-03", "amount", "1.00", "type", "EXPENSE", "description", "X", "categoryId", food),
                Map.of("externalId", "x1", "date", "2026-11-03", "amount", "1.00", "type", "EXPENSE", "description", "X\nY", "categoryId", food),
                Map.of("externalId", "x1", "date", "1990-11-03", "amount", "1.00", "type", "EXPENSE", "description", "X", "categoryId", food));
        for (Map<String, Object> item : bad) {
            Map<String, Object> ok = Map.of("externalId", "ok1", "date", "2026-11-03", "amount", "5.00", "type", "EXPENSE", "description", "Ok", "categoryId", food);
            MockHttpServletResponse r = commit(f, Map.of("accountId", f.account(), "entries", List.of(ok, item)));
            assertThat(r.getStatus()).as(item.toString()).isEqualTo(400);
        }
        assertThat(balance(f)).as("nada gravado").isEqualTo("100.00");
        assertThat(commit(f, Map.of("accountId", f.account(), "entries", List.of())).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Isolamento: conta ou categoria de outro espaço, por prévia ou importação")
    void isolation() throws Exception {
        Fx victim = fixture();
        Fx eve = fixture();
        MockHttpServletResponse foreignAccount = preview(eve.s(), ws(eve.s(), ""), victim.account(), "extrato.ofx", sample("extrato.ofx"));
        assertThat(body(foreignAccount).get("message").asString()).isEqualTo("Account not found");
        assertThat(preview(eve.s(), ws(victim.s(), ""), victim.account(), "extrato.ofx", sample("extrato.ofx")).getStatus()).isEqualTo(404);
        MockHttpServletResponse foreignCommit = call(post(ws(eve.s(), "/imports/commit")), eve.s().token(), Map.of("accountId", eve.account(),
                "entries", List.of(Map.of("externalId", "e1", "date", "2026-11-03", "amount", "1.00", "type", "EXPENSE",
                        "description", "X", "categoryId", victim.cats().get("Alimentação/EXPENSE")))));
        assertThat(body(foreignCommit).get("message").asString()).isEqualTo("Category not found");
        assertThat(call(post(ws(victim.s(), "/imports/commit")), eve.s().token(), Map.of("accountId", victim.account(),
                "entries", List.of(Map.of("externalId", "e2", "date", "2026-11-03", "amount", "1.00", "type", "EXPENSE",
                        "description", "X", "categoryId", victim.cats().get("Alimentação/EXPENSE"))))).getStatus()).isEqualTo(404);
        assertThat(balance(victim)).isEqualTo("100.00");
    }

    @Test
    @DisplayName("Conta arquivada não recebe importação")
    void archivedAccount() throws Exception {
        Fx f = fixture();
        call(put(ws(f.s(), "/accounts/" + f.account())), f.s().token(),
                Map.of("name", "Itaú", "type", "CHECKING", "initialBalance", "100.00", "archived", true));
        MockHttpServletResponse r = preview(f.s(), ws(f.s(), ""), f.account(), "extrato.ofx", sample("extrato.ofx"));
        assertThat(body(r).get("message").asString()).isEqualTo("Account is archived");
    }

    @Test
    @DisplayName("Sugestão respeita o tipo (regra de despesa não vale para receita) e ignora categoria arquivada; arquivada não importa")
    void suggestionKindAndArchived() throws Exception {
        Fx f = fixture();
        String salaryRuleCat = f.cats().get("Transporte/EXPENSE");
        call(post(ws(f.s(), "/rules")), f.s().token(), Map.of("pattern", "salario", "categoryId", salaryRuleCat));
        String pets = body(call(post(ws(f.s(), "/categories")), f.s().token(), Map.of("name", "Pets", "kind", "EXPENSE", "color", "#000000"))).get("id").asString();
        call(post(ws(f.s(), "/rules")), f.s().token(), Map.of("pattern", "padaria", "categoryId", pets));
        call(put(ws(f.s(), "/categories/" + pets)), f.s().token(), Map.of("name", "Pets", "color", "#000000", "archived", true));
        JsonNode p = preview(f, "extrato.ofx");
        assertThat(p.get("entries").get(1).get("suggestedCategory").get("id").asString()).as("receita: regra de despesa ignorada")
                .isEqualTo(f.cats().get("Outros/INCOME"));
        assertThat(p.get("entries").get(0).get("suggestedCategory").get("id").asString()).as("categoria arquivada não é sugerida")
                .isEqualTo(f.cats().get("Outros/EXPENSE"));
        MockHttpServletResponse r = commit(f, Map.of("accountId", f.account(), "entries", List.of(Map.of("externalId", "a1",
                "date", "2026-11-03", "amount", "1.00", "type", "EXPENSE", "description", "X", "categoryId", pets))));
        assertThat(body(r).get("message").asString()).isEqualTo("Category is archived");
    }

    @Test
    @DisplayName("Sem a categoria 'Outros' (excluída), a sugestão fica vazia para o usuário escolher; arquivo vazio → 400")
    void noFallbackAndEmptyFile() throws Exception {
        Fx f = fixture();
        call(delete(ws(f.s(), "/categories/" + f.cats().get("Outros/INCOME"))), f.s().token(), null);
        assertThat(preview(f, "extrato.ofx").get("entries").get(1).get("suggestedCategory").isNull()).isTrue();
        MockHttpServletResponse empty = preview(f.s(), ws(f.s(), ""), f.account(), "vazio.csv", new byte[0]);
        assertThat(body(empty).get("message").asString()).isEqualTo("Choose a file to import");
    }
}
