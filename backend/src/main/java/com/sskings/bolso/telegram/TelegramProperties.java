package com.sskings.bolso.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bot do Telegram (desligado por padrão).
 *
 * @param enabled            liga o bot (TELEGRAM_ENABLED)
 * @param token              token do @BotFather (TELEGRAM_BOT_TOKEN) — nunca vai para log nem para mensagem de erro
 * @param botUsername        nome do bot, sem @ (TELEGRAM_BOT_USERNAME), para o link t.me/&lt;bot&gt;?start=&lt;código&gt;
 * @param apiUrl             API do Telegram (trocada só nos testes)
 * @param pollTimeoutSeconds espera do long polling
 */
@ConfigurationProperties("bolso.telegram")
public record TelegramProperties(boolean enabled, String token, String botUsername, String apiUrl, Integer pollTimeoutSeconds) {

    static final Pattern TOKEN = Pattern.compile("^\\d+:[A-Za-z0-9_-]{30,}$");
    static final Pattern USERNAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,30}[Bb][Oo][Tt]$");

    public TelegramProperties {
        apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.telegram.org" : apiUrl;
        pollTimeoutSeconds = pollTimeoutSeconds == null ? 25 : pollTimeoutSeconds;
    }

    /** O que impede o bot de subir. A mensagem nunca inclui o token. */
    public List<String> problems() {
        List<String> p = new ArrayList<>();
        if (token == null || !TOKEN.matcher(token).matches()) p.add("TELEGRAM_BOT_TOKEN must be the token from @BotFather (123456789:AA...)");
        if (botUsername == null || !USERNAME.matcher(botUsername).matches()) p.add("TELEGRAM_BOT_USERNAME must be the bot username, without @ (ends with 'bot')");
        if (!apiUrl.startsWith("https://") && !apiUrl.startsWith("http://localhost") && !apiUrl.startsWith("http://127.0.0.1")) {
            p.add("bolso.telegram.api-url must use https");
        }
        if (pollTimeoutSeconds < 1 || pollTimeoutSeconds > 50) p.add("bolso.telegram.poll-timeout-seconds must be between 1 and 50");
        return p;
    }

    /** Sem o token: se o record for logado por engano, o segredo não vai junto. */
    @Override
    public String toString() {
        return "TelegramProperties[enabled=" + enabled + ", botUsername=" + botUsername + ", apiUrl=" + apiUrl
                + ", pollTimeoutSeconds=" + pollTimeoutSeconds + "]";
    }
}
