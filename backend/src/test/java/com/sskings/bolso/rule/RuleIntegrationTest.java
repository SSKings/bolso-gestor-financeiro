package com.sskings.bolso.rule;

import tools.jackson.databind.JsonNode;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Regras de categorização: "descrição contém X → categoria Y" (texto simples, nunca regex). */
class RuleIntegrationTest extends IntegrationTest {

    private Map<String, String> cats(Session s) throws Exception {
        Map<String, String> m = new HashMap<>();
        for (JsonNode c : body(call(get(ws(s, "/categories")), s.token(), null))) m.put(c.get("name").asString() + "/" + c.get("kind").asString(), c.get("id").asString());
        return m;
    }

    private MockHttpServletResponse rule(Session s, String pattern, String category) throws Exception {
        return call(post(ws(s, "/rules")), s.token(), Map.of("pattern", pattern, "categoryId", category));
    }

    private JsonNode suggest(Session s, String description) throws Exception {
        MockHttpServletResponse r = call(get(ws(s, "/rules/suggest")).param("description", description), s.token(), null);
        return r.getStatus() == 204 ? null : body(r);
    }

    @Test
    @DisplayName("Sugestão: sem acento e sem maiúsculas; a regra mais específica (mais longa) vence")
    void suggestion() throws Exception {
        Session s = register();
        Map<String, String> c = cats(s);
        assertThat(rule(s, "Uber", c.get("Transporte/EXPENSE")).getStatus()).isEqualTo(201);
        assertThat(rule(s, "uber eats", c.get("Alimentação/EXPENSE")).getStatus()).isEqualTo(201);
        assertThat(rule(s, "padaria são joão", c.get("Alimentação/EXPENSE")).getStatus()).isEqualTo(201);

        assertThat(suggest(s, "UBER *TRIP 1234").get("name").asString()).isEqualTo("Transporte");
        assertThat(suggest(s, "Uber Eats Restaurante").get("name").asString()).isEqualTo("Alimentação");
        assertThat(suggest(s, "PADARIA SAO JOAO LTDA").get("name").asString()).isEqualTo("Alimentação");
        assertThat(suggest(s, "Farmácia")).isNull();
    }

    @Test
    @DisplayName("Validação: 2 a 60 caracteres, sem caracteres de controle; padrão repetido (sem acento/maiúscula) → 409")
    void validation() throws Exception {
        Session s = register();
        String cat = cats(s).get("Transporte/EXPENSE");
        for (String bad : new String[]{"", "a", "x".repeat(61), "uber\nfake", "   "}) {
            assertThat(rule(s, bad, cat).getStatus()).as(bad).isEqualTo(400);
        }
        assertThat(body(rule(s, "\u0301\u0301\u0301", cat)).get("message").asString()).as("só acentos soltos: vazio sem eles")
                .isEqualTo("Pattern must have at least 2 characters");
        assertThat(rule(s, "Pedágio", cat).getStatus()).isEqualTo(201);
        assertThat(rule(s, "  PEDAGIO ", cat).getStatus()).isEqualTo(409);
        // regex não é interpretada: o padrão é texto literal
        assertThat(rule(s, "(a+)+$", cat).getStatus()).isEqualTo(201);
        assertThat(suggest(s, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!")).isNull();
        assertThat(suggest(s, "compra (a+)+$ teste").get("name").asString()).isEqualTo("Transporte");
        assertThat(call(get(ws(s, "/rules/suggest")).param("description", "x".repeat(201)), s.token(), null).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Listar e excluir; excluir a categoria leva as regras dela")
    void listAndDelete() throws Exception {
        Session s = register();
        String pets = body(call(post(ws(s, "/categories")), s.token(), Map.of("name", "Pets", "kind", "EXPENSE", "color", "#000000"))).get("id").asString();
        String id = body(rule(s, "petshop", pets)).get("id").asString();
        rule(s, "veterinario", pets);
        JsonNode list = body(call(get(ws(s, "/rules")), s.token(), null));
        assertThat(list.findValuesAsString("pattern")).containsExactly("petshop", "veterinario");
        assertThat(list.get(0).get("category").get("name").asString()).isEqualTo("Pets");
        assertThat(call(delete(ws(s, "/rules/" + id)), s.token(), null).getStatus()).isEqualTo(204);
        assertThat(call(delete(ws(s, "/categories/" + pets)), s.token(), null).getStatus()).isEqualTo(204);
        assertThat(body(call(get(ws(s, "/rules")), s.token(), null))).isEmpty();
    }

    @Test
    @DisplayName("Isolamento: categoria e regras de outro espaço são inexistentes")
    void isolation() throws Exception {
        Session victim = register();
        String vcat = cats(victim).get("Transporte/EXPENSE");
        String id = body(rule(victim, "segredo", vcat)).get("id").asString();
        Session eve = register();
        MockHttpServletResponse foreign = rule(eve, "x-hack", vcat);
        assertThat(body(foreign).get("message").asString()).isEqualTo("Category not found");
        assertThat(call(get(ws(victim, "/rules")), eve.token(), null).getStatus()).isEqualTo(404);
        assertThat(call(delete(ws(eve, "/rules/" + id)), eve.token(), null).getStatus()).isEqualTo(404);
        assertThat(suggest(eve, "segredo")).isNull();
        assertThat(body(call(get(ws(victim, "/rules")), victim.token(), null))).hasSize(1);
    }
}
