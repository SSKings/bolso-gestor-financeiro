package com.sskings.bolso.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

/**
 * Long polling numa thread própria, ligada ao ciclo de vida da aplicação. Falha de rede não derruba a API: tenta de
 * novo com espera crescente (até 60 s). Um update com erro não trava a fila.
 */
@Slf4j
public class TelegramPoller implements SmartLifecycle {

    static final long MAX_BACKOFF_SECONDS = 60;

    private final TelegramClient client;
    private final TelegramBot bot;
    private volatile boolean running;
    private Thread thread;
    private long offset;
    private boolean backlogSkipped;

    public TelegramPoller(TelegramClient client, TelegramBot bot) {
        this.client = client;
        this.bot = bot;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "telegram-bot");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram: bot iniciado (long polling)");
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        int failures = 0;
        while (running) {
            try {
                pollOnce();
                failures = 0;
            } catch (RuntimeException e) {
                if (!running) break;
                failures++;
                long wait = backoffSeconds(failures);
                log.warn("Telegram: falha ao buscar mensagens ({}). Nova tentativa em {} s", e.getMessage(), wait);
                try {
                    Thread.sleep(wait * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    void pollOnce() {
        if (!backlogSkipped) {
            offset = client.skipBacklog();
            backlogSkipped = true;
        }
        for (JsonNode update : client.getUpdates(offset)) {
            offset = update.path("update_id").asLong() + 1;
            try {
                bot.handle(update);
            } catch (RuntimeException e) {
                log.error("Telegram: erro ao tratar o update {}: {}", update.path("update_id").asLong(), e.getClass().getSimpleName());
            }
        }
    }

    static long backoffSeconds(int failures) {
        return Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(failures, 6));
    }
}
