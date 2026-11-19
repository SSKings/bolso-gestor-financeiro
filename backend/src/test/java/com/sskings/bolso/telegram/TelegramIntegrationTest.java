package com.sskings.bolso.telegram;

import tools.jackson.databind.JsonNode;
import com.sskings.bolso.support.FakeTelegram;
import com.sskings.bolso.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Telegram: vínculo por código de uso único, comandos agindo como o dono da conversa, lembretes e transporte. */
class TelegramIntegrationTest extends IntegrationTest {

    private static final String LINKED = "Conversa ligada";
    private static final String INVALID = "Código inválido ou vencido";
    private static final String UNLINKED_NOTICE = "Esta conversa foi desligada";

    @Autowired TelegramCommands commands;
    @Autowired TelegramReminders reminders;
    @Autowired TelegramConfig.ReminderJob reminderJob;
    @Autowired TelegramLinkRepository links;

    private record Fx(Session s, String account, Map<String, String> cats, long chat) {}

    @BeforeEach
    void noon() {
        clock.set(Instant.parse("2026-11-10T15:00:00Z"));                // 12h em São Paulo
    }

    private Fx fixture() throws Exception {
        Session s = register();
        String account = body(call(post(ws(s, "/accounts")), s.token(),
                Map.of("name", "Nubank", "type", "CHECKING", "initialBalance", "1000.00"))).get("id").asString();
        Map<String, String> cats = new HashMap<>();
        for (JsonNode c : body(call(get(ws(s, "/categories")), s.token(), null))) cats.put(c.get("name").asString() + "/" + c.get("kind").asString(), c.get("id").asString());
        return new Fx(s, account, cats, ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE));
    }

    private MockHttpServletResponse codeResponse(Session s, String workspace, String account) throws Exception {
        return call(post("/api/me/telegram/code"), s.token(), Map.of("workspaceId", workspace, "accountId", account));
    }

    private String code(Fx f) throws Exception {
        MockHttpServletResponse r = codeResponse(f.s(), f.s().workspaceId(), f.account());
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r).get("code").asString();
    }

    private Fx linked() throws Exception {
        Fx f = fixture();
        assertThat(say(f, "/start " + code(f))).contains(LINKED);
        return f;
    }

    private String say(Fx f, String text) {
        return commands.handle(f.chat(), true, text);
    }

    private JsonNode transactions(Fx f) throws Exception {
        return body(call(get(ws(f.s(), "/transactions?month=2026-11")), f.s().token(), null)).get("items");
    }

    private String balance(Fx f) throws Exception {
        return body(call(get(ws(f.s(), "/accounts/" + f.account())), f.s().token(), null)).get("balance").decimalValue().setScale(2).toPlainString();
    }

    @Test
    @DisplayName("Ponta a ponta pelo transporte real (polling + envio): liga a conversa e lança a despesa com a categoria da regra")
    void endToEnd() throws Exception {
        Fx f = fixture();
        call(post(ws(f.s(), "/rules")), f.s().token(), Map.of("pattern", "almoco", "categoryId", f.cats().get("Alimentação/EXPENSE")));
        JsonNode status = body(call(get("/api/me/telegram"), f.s().token(), null));
        assertThat(status.get("available").asBoolean()).isTrue();
        assertThat(status.get("botUsername").asString()).isEqualTo("BolsoTesteBot");
        assertThat(status.get("link").isNull()).isTrue();

        JsonNode code = body(codeResponse(f.s(), f.s().workspaceId(), f.account()));
        assertThat(code.get("link").asString()).isEqualTo("https://t.me/BolsoTesteBot?start=" + code.get("code").asString());
        assertThat(code.get("code").asString()).matches("[A-Za-z0-9_-]{43}");

        FakeTelegram tg = FakeTelegram.get();
        tg.send(f.chat(), "private", "/start " + code.get("code").asString());
        assertThat(tg.reply(f.chat())).contains(LINKED).contains("<b>Nubank</b>");
        tg.send(f.chat(), "private", "32,50 Almoço no centro");
        String reply = tg.reply(f.chat());
        assertThat(reply).contains("Despesa de <b>R$ 32,50</b>").contains("Alimentação · Nubank");

        JsonNode t = transactions(f).get(0);
        assertThat(t.get("source").asString()).isEqualTo("TELEGRAM");
        assertThat(t.get("description").asString()).isEqualTo("Almoço no centro");
        assertThat(t.get("category").get("name").asString()).isEqualTo("Alimentação");
        assertThat(t.get("date").asString()).isEqualTo("2026-11-10");
        assertThat(t.get("createdBy").get("id").asString()).isEqualTo(f.s().userId());

        JsonNode after = body(call(get("/api/me/telegram"), f.s().token(), null));
        assertThat(after.get("link").get("account").get("name").asString()).isEqualTo("Nubank");
    }

    @Test
    @DisplayName("Código: uso único, vence em 10 min, um novo invalida o anterior, mal formado é recusado — sempre a mesma resposta")
    void codeRules() throws Exception {
        Fx f = fixture();
        String first = code(f);
        String second = code(f);
        assertThat(say(f, "/start " + first)).startsWith(INVALID);
        assertThat(say(f, "/start x' OR 1=1 --")).startsWith(INVALID);
        assertThat(say(f, "/start " + second)).contains(LINKED);
        Fx other = fixture();
        assertThat(commands.handle(other.chat(), true, "/start " + second)).as("reuso, de outra conversa").startsWith(INVALID);

        String late = code(other);
        clock.advance(Duration.ofMinutes(10));
        assertThat(commands.handle(other.chat(), true, "/start " + late)).startsWith(INVALID);
        clock.advance(Duration.ofMinutes(-1));
        String ok = code(other);
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertThat(commands.handle(other.chat(), true, "/start " + ok)).contains(LINKED);
    }

    @Test
    @DisplayName("Grupo: recusa tudo e não consome o código; conversa sem vínculo recebe instruções e nada é lançado")
    void groupsAndUnlinked() throws Exception {
        Fx f = fixture();
        String code = code(f);
        assertThat(commands.handle(f.chat(), false, "/start " + code)).contains("conversa privada");
        assertThat(commands.handle(f.chat(), false, "10 pizza")).contains("conversa privada");
        assertThat(say(f, "10 pizza")).contains("Perfil → Telegram");
        assertThat(say(f, "/saldo")).contains("Perfil → Telegram");
        assertThat(transactions(f)).isEmpty();
        assertThat(say(f, "/start " + code)).as("o código continuou valendo").contains(LINKED);
        assertThat(commands.handle(f.chat(), true, "   ")).isNull();
        assertThat(commands.handle(f.chat(), true, null)).isNull();
    }

    @Test
    @DisplayName("Gerar código: só para espaço do qual é membro e conta ativa dele; sem login → 401")
    void codeAccess() throws Exception {
        Fx victim = fixture();
        Fx eve = fixture();
        assertThat(codeResponse(eve.s(), victim.s().workspaceId(), victim.account()).getStatus()).isEqualTo(404);
        assertThat(body(codeResponse(eve.s(), eve.s().workspaceId(), victim.account())).get("message").asString()).isEqualTo("Account not found");
        call(put(ws(eve.s(), "/accounts/" + eve.account())), eve.s().token(),
                Map.of("name", "Nubank", "type", "CHECKING", "initialBalance", "1000.00", "archived", true));
        assertThat(body(codeResponse(eve.s(), eve.s().workspaceId(), eve.account())).get("message").asString()).isEqualTo("Account is archived");
        assertThat(call(post("/api/me/telegram/code"), null, Map.of("workspaceId", victim.s().workspaceId(), "accountId", victim.account()))
                .getStatus()).isEqualTo(401);
        assertThat(call(get("/api/me/telegram"), null, null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Revogação: sair (revoga os tokens) desliga a conversa; o código de antes do logout também deixa de valer")
    void logoutRevokes() throws Exception {
        Fx f = linked();
        call(post("/api/auth/logout"), f.s().token(), null);
        assertThat(say(f, "10 pizza")).startsWith(UNLINKED_NOTICE);
        assertThat(links.findByChatId(f.chat())).isEmpty();
        assertThat(say(f, "10 pizza")).contains("Perfil → Telegram");
    }

    @Test
    @DisplayName("Revogação: quem sai do espaço compartilhado perde o acesso pelo Telegram na hora")
    void leavingWorkspaceRevokes() throws Exception {
        Fx owner = fixture();
        Session bia = register("Bia", email());
        String invite = body(call(post(ws(owner.s(), "/invites")), owner.s().token(), null)).get("code").asString();
        assertThat(call(post("/api/invites/" + invite + "/accept"), bia.token(), null).getStatus()).isEqualTo(200);
        String code = body(codeResponse(bia, owner.s().workspaceId(), owner.account())).get("code").asString();
        long chat = owner.chat() + 1;
        assertThat(commands.handle(chat, true, "/start " + code)).contains(LINKED);
        assertThat(commands.handle(chat, true, "5 café")).contains("Despesa");
        assertThat(call(post("/api/workspaces/" + owner.s().workspaceId() + "/leave"), bia.token(), null).getStatus()).isEqualTo(204);
        assertThat(commands.handle(chat, true, "/saldo")).startsWith(UNLINKED_NOTICE);
        assertThat(transactions(owner)).hasSize(1);
    }

    @Test
    @DisplayName("Lançamentos: valor no começo ou no fim, R$ e milhar; receita; texto do usuário escapado no HTML")
    void launching() throws Exception {
        Fx f = linked();
        assertThat(say(f, "uber 23,10")).contains("R$ 23,10").contains("Outros");
        assertThat(say(f, "R$ 1.234,56 aluguel")).contains("R$ 1.234,56").contains("aluguel");
        assertThat(TelegramCommands.positive("1.500")).as("ponto com 3 dígitos: milhar").isEqualByComparingTo("1500");
        assertThat(TelegramCommands.positive("1,5")).isEqualByComparingTo("1.50");
        assertThat(TelegramCommands.positive("1,500")).as("vírgula com 3 dígitos: recusado no chat").isNull();
        assertThat(TelegramCommands.positive("1,234,567")).isNull();
        assertThat(say(f, "/receita 3500 salário")).contains("Receita de <b>R$ 3.500,00</b>").contains("Outros");
        assertThat(say(f, "/despesa 7")).contains("Despesa pelo Telegram");
        String html = say(f, "<b>pizza</b> & <a href=x>y</a> 10");
        assertThat(html).contains("&lt;b&gt;pizza&lt;/b&gt; &amp; &lt;a href=x&gt;y&lt;/a&gt;").doesNotContain("<a href");
        assertThat(balance(f)).isEqualTo("3225.34");                    // 1000 − 23,10 − 1234,56 + 3500 − 7 − 10
        assertThat(transactions(f).findValuesAsString("description")).contains("<b>pizza</b> & <a href=x>y</a>");
    }

    @Test
    @DisplayName("Mensagens recusadas: sem valor, zero, negativo, acima do teto, 3 casas, longa demais, comando desconhecido")
    void rejected() throws Exception {
        Fx f = linked();
        for (String bad : new String[]{"pizza", "0 pizza", "-5 pizza", "pizza -5", "1000000000 pizza", "5,5,5 pizza", "10,555 pizza", "/receita"}) {
            assertThat(say(f, bad)).as(bad).containsAnyOf("Não entendi o valor", "Use:");
        }
        assertThat(say(f, "x".repeat(301))).isEqualTo("Mensagem longa demais.");
        assertThat(say(f, "/apagartudo")).startsWith("Não conheço esse comando");
        assertThat(say(f, "/ajuda@BolsoTesteBot")).startsWith("<b>Como usar</b>");
        assertThat(say(f, "/start")).startsWith("<b>Como usar</b>");
        assertThat(transactions(f)).isEmpty();
    }

    @Test
    @DisplayName("Descrição: caracteres de controle removidos, corte em 140; conta arquivada depois do vínculo → aviso, nada gravado")
    void descriptionAndArchivedAccount() throws Exception {
        Fx f = linked();
        say(f, "5 a\u0007b\u001bc " + "x".repeat(200));
        String d = transactions(f).get(0).get("description").asString();
        assertThat(d).startsWith("abc xxx").hasSize(140);
        call(put(ws(f.s(), "/accounts/" + f.account())), f.s().token(),
                Map.of("name", "Nubank", "type", "CHECKING", "initialBalance", "1000.00", "archived", true));
        assertThat(say(f, "9 pão")).contains("arquivada ou excluída");
        assertThat(transactions(f)).hasSize(1);
    }

    @Test
    @DisplayName("Sem categoria 'Outros' nem regra → pede para criar; regra de receita não vale para despesa")
    void noCategory() throws Exception {
        Fx f = linked();
        call(post(ws(f.s(), "/rules")), f.s().token(), Map.of("pattern", "pix", "categoryId", f.cats().get("Salário/INCOME")));
        call(delete(ws(f.s(), "/categories/" + f.cats().get("Outros/EXPENSE"))), f.s().token(), null);
        assertThat(say(f, "10 pix amigo")).contains("Crie uma regra");
        assertThat(say(f, "/receita 10 pix amigo")).contains("Salário");
    }

    @Test
    @DisplayName("/saldo e /contas mostram só o espaço ligado; /desfazer apaga só o último lançamento do Telegram, até 24 h")
    void queriesAndUndo() throws Exception {
        Fx f = linked();
        Fx other = linked();
        say(other, "999 segredo");
        call(post(ws(f.s(), "/transactions")), f.s().token(), Map.of("type", "EXPENSE", "amount", "100.00", "date", "2026-11-09",
                "description", "Manual", "accountId", f.account(), "categoryId", f.cats().get("Alimentação/EXPENSE")));
        call(post(ws(f.s(), "/recurrences")), f.s().token(), Map.of("type", "EXPENSE", "description", "Internet <fibra>", "amount", "99.90",
                "dayOfMonth", 5, "categoryId", f.cats().get("Moradia/EXPENSE"), "accountId", f.account(), "startMonth", "2026-11"));
        call(post(ws(f.s(), "/recurrences")), f.s().token(), Map.of("type", "EXPENSE", "description", "Luz", "amount", "150.00",
                "dayOfMonth", 11, "categoryId", f.cats().get("Moradia/EXPENSE"), "accountId", f.account(), "startMonth", "2026-11"));

        assertThat(say(f, "/saldo")).contains("Nubank: R$ 900,00").contains("Total: R$ 900,00").doesNotContain("segredo");
        String bills = say(f, "/contas");
        assertThat(bills).contains("Internet &lt;fibra&gt; — R$ 99,90 (⚠️ atrasada desde 05/11)").contains("Luz — R$ 150,00 (vence amanhã)");
        assertThat(commands.handle(other.chat(), true, "/contas")).startsWith("Nenhuma conta fixa");

        say(f, "10 primeiro");
        clock.advance(Duration.ofMinutes(1));
        say(f, "20 segundo");
        assertThat(say(f, "/desfazer")).contains("Apagado: segundo — R$ 20,00");
        assertThat(say(f, "/desfazer")).contains("Apagado: primeiro");
        assertThat(say(f, "/desfazer")).as("o manual fica").startsWith("Nada para desfazer");
        say(f, "30 velho");
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        assertThat(say(f, "/desfazer")).startsWith("Nada para desfazer");
        clock.advance(Duration.ofHours(-24));                            // o token do teste vale 12 h
        assertThat(transactions(f).findValuesAsString("description")).containsExactlyInAnyOrder("Manual", "velho");
        assertThat(commands.handle(other.chat(), true, "/desfazer")).contains("segredo");
    }

    @Test
    @DisplayName("/sair e 'desligar' no app apagam o vínculo e os códigos pendentes")
    void unlink() throws Exception {
        Fx f = linked();
        assertThat(say(f, "/sair")).startsWith("Conversa desligada");
        assertThat(say(f, "/saldo")).contains("Perfil → Telegram");
        Fx g = linked();
        String pending = code(g);
        assertThat(call(delete("/api/me/telegram"), g.s().token(), null).getStatus()).isEqualTo(204);
        assertThat(commands.handle(g.chat(), true, "/saldo")).contains("Perfil → Telegram");
        assertThat(commands.handle(g.chat(), true, "/start " + pending)).startsWith(INVALID);
        assertThat(body(call(get("/api/me/telegram"), g.s().token(), null)).get("link").isNull()).isTrue();
    }

    @Test
    @DisplayName("Uma conversa por pessoa e uma pessoa por conversa: ligar de novo substitui o vínculo anterior")
    void oneLinkEach() throws Exception {
        Fx f = linked();
        long newChat = f.chat() + 7;
        assertThat(commands.handle(newChat, true, "/start " + code(f))).contains(LINKED);
        assertThat(say(f, "/saldo")).contains("Perfil → Telegram");
        Fx g = fixture();
        assertThat(commands.handle(newChat, true, "/start " + code(g))).contains(LINKED);
        assertThat(links.findByUserId(java.util.UUID.fromString(f.s().userId()))).isEmpty();
        commands.handle(newChat, true, "5 café");
        assertThat(transactions(g)).hasSize(1);
        assertThat(transactions(f)).isEmpty();
    }

    @Test
    @DisplayName("Lembrete: atrasadas, hoje e amanhã (não as de depois); no máximo um por dia; falha no envio tenta de novo")
    void reminders() throws Exception {
        Fx f = linked();
        Fx quiet = linked();
        call(post(ws(f.s(), "/recurrences")), f.s().token(), Map.of("type", "EXPENSE", "description", "Aluguel", "amount", "1500.00",
                "dayOfMonth", 11, "categoryId", f.cats().get("Moradia/EXPENSE"), "accountId", f.account(), "startMonth", "2026-11"));
        call(post(ws(f.s(), "/recurrences")), f.s().token(), Map.of("type", "EXPENSE", "description", "Academia", "amount", "90.00",
                "dayOfMonth", 14, "categoryId", f.cats().get("Saúde/EXPENSE"), "accountId", f.account(), "startMonth", "2026-11"));

        FakeTelegram tg = FakeTelegram.get();
        tg.failSend = true;
        try {
            reminderJob.run();
        } finally {
            tg.failSend = false;
        }
        assertThat(tg.sentTo(f.chat())).isEmpty();
        reminderJob.run();
        String text = tg.reply(f.chat());
        assertThat(text).startsWith("🔔").contains("Aluguel — R$ 1.500,00 (vence amanhã)").doesNotContain("Academia");
        reminderJob.run();
        assertThat(tg.sentTo(f.chat())).as("um por dia").isEmpty();
        assertThat(tg.sentTo(quiet.chat())).as("nada a avisar").isEmpty();
        assertThat(reminders.pending()).noneMatch(r -> r.chatId() == f.chat() || r.chatId() == quiet.chat());

        clock.advance(Duration.ofDays(1));
        assertThat(reminders.pending()).filteredOn(r -> r.chatId() == f.chat()).singleElement()
                .extracting(TelegramReminders.Reminder::text).asString().contains("Aluguel — R$ 1.500,00 (vence hoje)");
        clock.advance(Duration.ofDays(-1));                              // o token do teste vale 12 h
        assertThat(call(post("/api/auth/logout"), f.s().token(), null).getStatus()).isEqualTo(204);
        clock.advance(Duration.ofDays(2));
        assertThat(reminders.pending()).noneMatch(r -> r.chatId() == f.chat());
        assertThat(links.findByChatId(f.chat())).as("vínculo revogado é apagado").isEmpty();
    }

    @Test
    @DisplayName("Entre gerar e usar o código: conta arquivada ou pessoa fora do espaço → código não liga nada")
    void stateChangesBeforeConsume() throws Exception {
        Fx f = fixture();
        String code = code(f);
        call(put(ws(f.s(), "/accounts/" + f.account())), f.s().token(),
                Map.of("name", "Nubank", "type", "CHECKING", "initialBalance", "1000.00", "archived", true));
        assertThat(say(f, "/start " + code)).startsWith(INVALID);

        Fx owner = fixture();
        Session bia = register("Bia", email());
        String invite = body(call(post(ws(owner.s(), "/invites")), owner.s().token(), null)).get("code").asString();
        call(post("/api/invites/" + invite + "/accept"), bia.token(), null);
        String biaCode = body(codeResponse(bia, owner.s().workspaceId(), owner.account())).get("code").asString();
        call(post("/api/workspaces/" + owner.s().workspaceId() + "/leave"), bia.token(), null);
        assertThat(commands.handle(owner.chat() + 3, true, "/start " + biaCode)).startsWith(INVALID);
        assertThat(links.findByChatId(owner.chat() + 3)).isEmpty();
    }

    @Test
    @DisplayName("Sem valor no /despesa, receita sem descrição, todas as contas arquivadas e conta vencendo em alguns dias")
    void remainingBranches() throws Exception {
        Fx f = linked();
        assertThat(say(f, "/despesa")).startsWith("Use: <code>/despesa");
        assertThat(say(f, "/receita 50")).contains("Receita pelo Telegram");
        call(post(ws(f.s(), "/recurrences")), f.s().token(), Map.of("type", "EXPENSE", "description", "Seguro", "amount", "80.00",
                "dayOfMonth", 14, "categoryId", f.cats().get("Moradia/EXPENSE"), "accountId", f.account(), "startMonth", "2026-11"));
        assertThat(say(f, "/contas")).contains("Seguro — R$ 80,00 (vence 14/11)");
        for (JsonNode a : body(call(get(ws(f.s(), "/accounts")), f.s().token(), null))) {
            call(put(ws(f.s(), "/accounts/" + a.get("id").asString())), f.s().token(), Map.of("name", a.get("name").asString(),
                    "type", a.get("type").asString(), "initialBalance", a.get("initialBalance").asString(), "archived", true));
        }
        assertThat(say(f, "/saldo")).isEqualTo("Nenhuma conta ativa.");
    }
}
