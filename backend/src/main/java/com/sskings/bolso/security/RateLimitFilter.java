package com.sskings.bolso.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite por origem, em janela de 1 minuto, nas rotas que não exigem login (cadastro, login) e no aceite de convite.
 * O caminho é o normalizado (RequestPaths: "%6Cogin" conta como "login"); IPv6 conta pelo bloco /64. Em memória:
 * vale por instância — com várias réplicas, limite também no proxy.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final int perMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastSweep = -1;

    public RateLimitFilter(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    private record Window(long minute, AtomicInteger count) {}

    static boolean limited(String method, String path) {
        return "POST".equals(method) && (path.startsWith("/api/auth/") || path.startsWith("/api/invites/"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !limited(request.getMethod(), RequestPaths.of(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long seconds = clock.instant().getEpochSecond();
        long minute = seconds / 60;
        String key = sourceKey(request.getRemoteAddr());
        Window w = windows.compute(key, (k, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        if (w.count().incrementAndGet() > perMinute) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(60 - seconds % 60));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
            return;
        }
        if (windows.size() > 10_000 && lastSweep != minute) {
            lastSweep = minute;
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        chain.doFilter(request, response);
    }

    /** IPv4 inteiro; IPv6 pelo /64 (o cliente troca de endereço dentro do bloco à vontade). */
    static String sourceKey(String ip) {
        if (ip == null || !ip.contains(":")) return String.valueOf(ip);
        try {
            byte[] b = java.net.InetAddress.getByName(ip).getAddress();   // literal: getRemoteAddr nunca traz nome
            if (b.length != 16) return ip;
            return String.format("%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64", b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]);
        } catch (java.net.UnknownHostException e) {
            return ip;
        }
    }
}
