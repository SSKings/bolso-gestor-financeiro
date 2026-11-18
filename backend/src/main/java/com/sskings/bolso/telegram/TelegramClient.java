package com.sskings.bolso.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bot API do Telegram: só saída HTTPS com long polling (sem webhook, nada exposto). O token faz parte da URL; toda
 * mensagem de erro passa por {@link #redact} antes de virar exceção ou log.
 */
public class TelegramClient {

    private final RestClient rest;
    private final String token;
    private final int pollTimeoutSeconds;

    public TelegramClient(RestClient.Builder builder, TelegramProperties props) {
        this.token = props.token();
        this.pollTimeoutSeconds = props.pollTimeoutSeconds();
        this.rest = builder.baseUrl(props.apiUrl() + "/bot" + token).build();
    }

    public List<JsonNode> getUpdates(long offset) {
        return getUpdates(offset, pollTimeoutSeconds);
    }

    /** Descarta o que acumulou com a API fora do ar: mensagens antigas não viram lançamentos de surpresa no restart. */
    public long skipBacklog() {
        List<JsonNode> last = getUpdates(-1, 0);
        return last.isEmpty() ? 0 : last.get(last.size() - 1).path("update_id").asLong() + 1;
    }

    private List<JsonNode> getUpdates(long offset, int timeout) {
        JsonNode result = call("getUpdates", Map.of("offset", offset, "timeout", timeout, "allowed_updates", List.of("message")));
        List<JsonNode> updates = new ArrayList<>();
        result.forEach(updates::add);
        return updates;
    }

    public void sendMessage(long chatId, String html) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("link_preview_options", Map.of("is_disabled", true));
        call("sendMessage", body);
    }

    private JsonNode call(String method, Map<String, Object> body) {
        JsonNode response;
        try {
            response = rest.post().uri("/" + method).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new TelegramException(method + " failed: " + redact(e.getMessage()));
        }
        if (response == null || !response.path("ok").asBoolean(false)) {
            String description = response == null ? "empty response" : response.path("description").asText("unknown error");
            throw new TelegramException(method + " failed: " + redact(description));
        }
        return response.path("result");
    }

    String redact(String message) {
        return message == null ? null : message.replace(token, "<token>");
    }
}
