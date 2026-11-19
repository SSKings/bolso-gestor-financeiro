package com.sskings.bolso.telegram;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sskings.bolso.support.FakeTelegram;
import com.sskings.bolso.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TelegramTransportTest {

    private static final String TOKEN = FakeTelegram.TOKEN;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static TelegramProperties props(String token, String username, String apiUrl, Integer timeout) {
        return new TelegramProperties(true, token, username, apiUrl, timeout);
    }

    private static JsonNode update(String json) throws Exception {
        return JSON.readTree(json);
    }

    @Test
    @DisplayName("Configuração: token e nome do bot no formato certo, API em https, espera de 1 a 50 s; toString sem o token")
    void properties() {
        assertThat(props(TOKEN, "BolsoBot", null, null).problems()).isEmpty();
        assertThat(props(TOKEN, "BolsoBot", null, null).apiUrl()).isEqualTo("https://api.telegram.org");
        assertThat(props("abc", "Bolso", "http://evil.example", 99).problems()).hasSize(4);
        assertThat(props(null, null, null, 0).problems()).hasSize(3);
        assertThat(props(TOKEN, "BolsoBot", null, null).toString()).doesNotContain(TOKEN).doesNotContain("TEST-token");
    }

    @Test
    @DisplayName("Ligado com configuração inválida: a aplicação não sobe, e o erro não mostra o token; desligado: sem bot")
    void configStartup() {
        new ApplicationContextRunner().withUserConfiguration(TelegramConfig.class)
                .withPropertyValues("bolso.telegram.enabled=true", "bolso.telegram.token=999:short-secret-value",
                        "bolso.telegram.bot-username=BolsoBot")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("TELEGRAM_BOT_TOKEN")
                            .message().doesNotContain("short-secret-value");
                });
        new ApplicationContextRunner().withUserConfiguration(TelegramConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(TelegramPoller.class).doesNotHaveBean(TelegramClient.class);
                    assertThat(ctx.getBean(TelegramProperties.class).enabled()).isFalse();
                });
    }

    @Test
    @DisplayName("Cliente: erro de rede ou da API vira TelegramException sem o token (ele faz parte da URL)")
    void clientRedactsToken() {
        TelegramClient down = new TelegramClient(RestClient.builder(), props(TOKEN, "BolsoBot", "http://127.0.0.1:1", 1));
        assertThatThrownBy(() -> down.sendMessage(1, "oi")).isInstanceOf(TelegramException.class)
                .hasMessageStartingWith("sendMessage failed:").hasMessageContaining("<token>").message().doesNotContain(TOKEN);
        String wrong = "123456789:WRONG-token-abcdefghijklmnopqrstuvwx";
        TelegramClient rejected = new TelegramClient(RestClient.builder(), props(wrong, "BolsoBot", FakeTelegram.get().url(), 1));
        assertThatThrownBy(rejected::skipBacklog).hasMessage("getUpdates failed: Unauthorized");
        assertThat(rejected.redact(null)).isNull();
    }

    @Test
    @DisplayName("Bot: ignora mensagens de bots, sem texto ou sem 'message'; repassa se é conversa privada")
    void botFilters() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramCommands commands = mock(TelegramCommands.class);
        when(commands.handle(anyLong(), anyBoolean(), anyString())).thenReturn("ok");
        TelegramBot bot = new TelegramBot(client, commands, new MutableClock());
        bot.handle(update("{\"update_id\":1}"));
        bot.handle(update("{\"message\":{\"chat\":{\"id\":5,\"type\":\"private\"},\"from\":{\"is_bot\":true},\"text\":\"10 x\"}}"));
        bot.handle(update("{\"message\":{\"chat\":{\"id\":5,\"type\":\"private\"},\"from\":{\"is_bot\":false},\"sticker\":{}}}"));
        verifyNoInteractions(commands);
        bot.handle(update("{\"message\":{\"chat\":{\"id\":5,\"type\":\"group\"},\"from\":{\"is_bot\":false},\"text\":\"10 x\"}}"));
        bot.handle(update("{\"message\":{\"chat\":{\"id\":6,\"type\":\"private\"},\"from\":{\"is_bot\":false},\"text\":\"10 y\"}}"));
        verify(commands).handle(5, false, "10 x");
        verify(commands).handle(6, true, "10 y");
        verify(client).sendMessage(5, "ok");
        when(commands.handle(anyLong(), anyBoolean(), anyString())).thenReturn(null);
        bot.handle(update("{\"message\":{\"chat\":{\"id\":7,\"type\":\"private\"},\"from\":{\"is_bot\":false},\"text\":\" \"}}"));
        verify(client, never()).sendMessage(eq(7L), anyString());
    }

    @Test
    @DisplayName("Limite: 20 mensagens por minuto por conversa; as outras conversas não são afetadas; libera depois de 1 minuto")
    void rateLimit() {
        MutableClock clock = new MutableClock();
        clock.set(Instant.parse("2026-11-10T15:00:00Z"));
        TelegramBot bot = new TelegramBot(mock(TelegramClient.class), mock(TelegramCommands.class), clock);
        for (int i = 0; i < 20; i++) assertThat(bot.allowed(1)).isTrue();
        assertThat(bot.allowed(1)).isFalse();
        assertThat(bot.allowed(1)).isFalse();
        assertThat(bot.allowed(2)).isTrue();
        clock.advance(Duration.ofSeconds(61));
        assertThat(bot.allowed(1)).isTrue();
    }

    @Test
    @DisplayName("Poller: descarta o acumulado na primeira rodada, avança o offset e um update com erro não trava a fila")
    void poller() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        TelegramBot bot = mock(TelegramBot.class);
        JsonNode a = update("{\"update_id\":10}");
        JsonNode b = update("{\"update_id\":11}");
        when(client.skipBacklog()).thenReturn(10L);
        when(client.getUpdates(10L)).thenReturn(List.of(a, b));
        doThrow(new IllegalStateException("boom")).when(bot).handle(a);
        TelegramPoller poller = new TelegramPoller(client, bot);
        poller.pollOnce();
        poller.pollOnce();
        verify(client, times(1)).skipBacklog();
        verify(bot).handle(b);
        verify(client).getUpdates(12L);
        assertThat(TelegramPoller.backoffSeconds(1)).isEqualTo(2);
        assertThat(TelegramPoller.backoffSeconds(5)).isEqualTo(32);
        assertThat(TelegramPoller.backoffSeconds(30)).isEqualTo(60);
    }

    @Test
    @DisplayName("Poller: com o Telegram fora, espera e tenta de novo sem derrubar nada; desligar interrompe a espera")
    void pollerLifecycle() throws Exception {
        TelegramClient client = mock(TelegramClient.class);
        when(client.skipBacklog()).thenThrow(new TelegramException("down"));
        TelegramPoller poller = new TelegramPoller(client, mock(TelegramBot.class));
        poller.start();
        poller.start();                                                  // segunda chamada não cria outra thread
        assertThat(poller.isRunning()).isTrue();
        verify(client, timeout(2000).times(1)).skipBacklog();            // falhou e entrou na espera (2 s)
        Thread t = poller.thread();
        poller.stop();
        t.join(2000);
        assertThat(t.isAlive()).as("a espera é interrompida no desligamento").isFalse();
        assertThat(poller.isRunning()).isFalse();
        verify(client, times(1)).skipBacklog();
    }

    @Test
    @DisplayName("Formatação: valores em reais (com sinal) e HTML escapado")
    void text() {
        assertThat(BotText.brl(new java.math.BigDecimal("-1234.5"))).isEqualTo("−R$ 1.234,50");
        assertThat(BotText.brl(java.math.BigDecimal.ZERO)).isEqualTo("R$ 0,00");
        assertThat(BotText.esc("<a&b>")).isEqualTo("&lt;a&amp;b&gt;");
        assertThat(BotText.esc(null)).isEmpty();
    }
}
