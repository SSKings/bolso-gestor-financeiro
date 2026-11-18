package com.sskings.bolso.security;

import com.sskings.bolso.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock();

    private int call(RateLimitFilter f, String method, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        if (res.getStatus() == 429) assertThat(res.getHeader("Retry-After")).isNotBlank();
        return res.getStatus();
    }

    @Test
    @DisplayName("Login, cadastro e aceite de convite dividem o limite por IP; a janela vira no minuto seguinte")
    void limitAndWindow() throws Exception {
        RateLimitFilter f = new RateLimitFilter(3, clock);
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/register", "1.1.1.1")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/invites/abc/accept", "1.1.1.1")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(429);
        assertThat(call(f, "POST", "/api/auth/login", "2.2.2.2")).isEqualTo(200);
        clock.advance(Duration.ofSeconds(61));
        assertThat(call(f, "POST", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
    }

    @Test
    @DisplayName("Outras rotas e métodos não são limitados aqui")
    void otherRoutesNotLimited() throws Exception {
        RateLimitFilter f = new RateLimitFilter(1, clock);
        for (int i = 0; i < 5; i++) {
            assertThat(call(f, "GET", "/api/auth/login", "1.1.1.1")).isEqualTo(200);
            assertThat(call(f, "POST", "/api/workspaces", "1.1.1.1")).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Caminho codificado (%6C) conta no mesmo limite (lição da revisão 6 do ShopEasy)")
    void encodedPath() throws Exception {
        RateLimitFilter f = new RateLimitFilter(1, clock);
        assertThat(call(f, "POST", "/api/auth/%6Cogin", "3.3.3.3")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/login", "3.3.3.3")).isEqualTo(429);
    }

    @Test
    @DisplayName("IPv6 conta pelo /64: trocar de endereço no mesmo bloco não renova o limite")
    void ipv6Block() throws Exception {
        RateLimitFilter f = new RateLimitFilter(2, clock);
        assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:2::1")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:2:ffff:ffff:ffff:ffff")).isEqualTo(200);
        assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:2::abcd")).isEqualTo(429);
        assertThat(call(f, "POST", "/api/auth/login", "2001:db8:1:3::1")).isEqualTo(200);
        assertThat(RateLimitFilter.sourceKey("2001:db8:0:0:1::1")).isEqualTo("2001:0db8:0000:0000::/64");
        assertThat(RateLimitFilter.sourceKey("1.2.3.4")).isEqualTo("1.2.3.4");
        assertThat(RateLimitFilter.sourceKey(null)).isEqualTo("null");
        assertThat(RateLimitFilter.sourceKey("::ffff:1.2.3.4")).as("IPv4 escrito como IPv6 conta como o próprio IPv4").isEqualTo("1.2.3.4");
        assertThat(RateLimitFilter.sourceKey("::ffff:1.2.3.4")).isEqualTo(RateLimitFilter.sourceKey("1.2.3.4"));
    }

    @Test
    @DisplayName("Memória limitada: acima de 10 mil origens, as janelas de minutos passados são descartadas")
    void sweep() throws Exception {
        RateLimitFilter f = new RateLimitFilter(5, clock);
        for (int i = 0; i < 10_001; i++) call(f, "POST", "/api/auth/login", "10." + (i >> 16 & 255) + "." + (i >> 8 & 255) + "." + (i & 255));
        assertThat(f.tracked()).isEqualTo(10_001);
        clock.advance(Duration.ofSeconds(61));
        call(f, "POST", "/api/auth/login", "9.9.9.9");
        assertThat(f.tracked()).isEqualTo(1);
    }

    @Test
    @DisplayName("429: corpo JSON padrão, Retry-After com os segundos que faltam no minuto; janela vale o minuto inteiro")
    void tooManyResponse() throws Exception {
        clock.set(java.time.Instant.parse("2026-11-10T15:00:15Z"));
        RateLimitFilter f = new RateLimitFilter(1, clock);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("7.7.7.7");
        MockFilterChain first = new MockFilterChain();
        f.doFilter(req, new MockHttpServletResponse(), first);
        assertThat(first.getRequest()).as("pedido liberado segue adiante").isNotNull();
        clock.advance(Duration.ofSeconds(30));                           // mesmo minuto, outro segundo
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain blocked = new MockFilterChain();
        f.doFilter(req, res, blocked);
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(blocked.getRequest()).as("bloqueado não segue").isNull();
        assertThat(res.getHeader("Retry-After")).isEqualTo("15");
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getContentAsString()).isEqualTo("{\"status\":429,\"message\":\"Too many requests\"}");
    }

    @Test
    @DisplayName("Com exatamente 10 mil origens ainda não limpa; acima disso, sim")
    void sweepBoundary() throws Exception {
        RateLimitFilter f = new RateLimitFilter(5, clock);
        for (int i = 0; i < 9_999; i++) call(f, "POST", "/api/auth/login", "10." + (i >> 16 & 255) + "." + (i >> 8 & 255) + "." + (i & 255));
        clock.advance(Duration.ofSeconds(61));
        call(f, "POST", "/api/auth/login", "9.9.9.9");
        assertThat(f.tracked()).isEqualTo(10_000);
    }
}
