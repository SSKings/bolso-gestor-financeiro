package com.sskings.bolso.telegram;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recebe cada update: ignora o que não é mensagem de texto de pessoa, limita mensagens por conversa (um estranho
 * mandando spam não ocupa o banco) e responde. O texto das mensagens nunca vai para o log (são dados financeiros).
 */
@Slf4j
public class TelegramBot {

    static final int MAX_PER_MINUTE = 20;
    static final int MAX_TRACKED_CHATS = 10_000;

    private final TelegramClient client;
    private final TelegramCommands commands;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> recent = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED_CHATS;
        }
    };

    public TelegramBot(TelegramClient client, TelegramCommands commands, Clock clock) {
        this.client = client;
        this.commands = commands;
        this.clock = clock;
    }

    public void handle(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode() || message.path("from").path("is_bot").asBoolean(false) || !message.path("text").isString()) return;
        long chatId = message.path("chat").path("id").asLong();
        if (!allowed(chatId)) return;
        String reply = commands.handle(chatId, "private".equals(message.path("chat").path("type").asString()), message.path("text").asString());
        if (reply != null) client.sendMessage(chatId, reply);
    }

    synchronized boolean allowed(long chatId) {
        Instant now = clock.instant();
        Deque<Instant> q = recent.computeIfAbsent(chatId, k -> new ArrayDeque<>());
        while (!q.isEmpty() && !q.peekFirst().isAfter(now.minusSeconds(60))) q.pollFirst();
        if (q.size() >= MAX_PER_MINUTE) {
            if (q.size() == MAX_PER_MINUTE) log.warn("Telegram: conversa {} passou de {} mensagens por minuto", chatId, MAX_PER_MINUTE);
            q.addLast(now);
            return false;
        }
        q.addLast(now);
        return true;
    }
}
