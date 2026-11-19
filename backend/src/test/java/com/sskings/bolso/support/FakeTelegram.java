package com.sskings.bolso.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API do Telegram de mentira (HTTP local) para testar o bot de ponta a ponta: o teste enfileira mensagens "do
 * usuário" e lê o que o bot respondeu. Também guarda os caminhos chamados (para conferir o token na URL).
 */
public final class FakeTelegram {

    public static final String TOKEN = "123456789:TEST-token-only-for-tests_abcdefghij";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static FakeTelegram instance;

    private final HttpServer server;
    private final BlockingQueue<JsonNode> updates = new LinkedBlockingQueue<>();
    private final List<JsonNode> sent = new CopyOnWriteArrayList<>();
    private final AtomicLong nextUpdate = new AtomicLong(1000);
    public volatile boolean failSend;

    private FakeTelegram() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            JsonNode body = JSON.readTree(ex.getRequestBody());
            ObjectNode resp = JSON.createObjectNode();
            if (!path.startsWith("/bot" + TOKEN + "/")) {
                resp.put("ok", false).put("description", "Unauthorized");
            } else if (path.endsWith("/getUpdates")) {
                ArrayNode result = resp.put("ok", true).putArray("result");
                if (body.path("offset").asLong() >= 0) {
                    try {
                        JsonNode u = updates.poll(body.path("timeout").asInt() == 0 ? 0 : 300, TimeUnit.MILLISECONDS);
                        if (u != null) result.add(u);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else if (path.endsWith("/sendMessage")) {
                if (failSend) {
                    resp.put("ok", false).put("description", "Bad Request: chat not found");
                } else {
                    sent.add(body);
                    resp.put("ok", true).putObject("result");
                }
            } else {
                resp.put("ok", true);
            }
            byte[] out = JSON.writeValueAsBytes(resp);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    public static synchronized FakeTelegram get() {
        if (instance == null) {
            try {
                instance = new FakeTelegram();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return instance;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Mensagem de texto de uma pessoa (chat privado, a não ser que chatType diga outra coisa). */
    public void send(long chatId, String chatType, String text) {
        ObjectNode u = JSON.createObjectNode().put("update_id", nextUpdate.incrementAndGet());
        ObjectNode m = u.putObject("message");
        m.putObject("chat").put("id", chatId).put("type", chatType);
        m.putObject("from").put("id", chatId).put("is_bot", false);
        if (text != null) m.put("text", text);
        updates.add(u);
    }

    /** Espera a próxima resposta do bot para a conversa (até 10 s). */
    public String reply(long chatId) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            for (JsonNode s : sent) {
                if (s.path("chat_id").asLong() == chatId) {
                    sent.remove(s);
                    return s.path("text").asString();
                }
            }
            Thread.sleep(20);
        }
        return null;
    }

    public List<JsonNode> sentTo(long chatId) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode s : sent) if (s.path("chat_id").asLong() == chatId) out.add(s);
        return out;
    }

}
