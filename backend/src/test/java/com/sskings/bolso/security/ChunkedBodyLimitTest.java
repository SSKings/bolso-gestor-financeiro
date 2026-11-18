package com.sskings.bolso.security;

import com.sskings.bolso.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corpo sem Content-Length (Transfer-Encoding: chunked) também respeita o limite: sem isso, bastava omitir o cabeçalho
 * para passar dos 64 KB. Precisa de um Tomcat de verdade — o MockMvc sempre calcula o tamanho.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ChunkedBodyLimitTest {

    @LocalServerPort int port;

    private HttpResponse<String> chunked(String path, int bytes) throws Exception {
        String body = "{\"email\":\"" + "a".repeat(bytes) + "@x.com\",\"password\":\"x\"}";
        // InputStream sem tamanho conhecido → o cliente manda em pedaços (chunked), sem Content-Length
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))))
                .build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Chunked acima de 64 KB → 413 com a mensagem padrão; abaixo do limite segue normal (401 de login errado)")
    void chunkedBodyIsLimited() throws Exception {
        HttpResponse<String> big = chunked("/api/auth/login", 100_000);
        assertThat(big.statusCode()).isEqualTo(413);
        assertThat(big.body()).isEqualTo("{\"status\":413,\"message\":\"Request body too large\",\"errors\":null}");
        assertThat(chunked("/api/auth/login", 20).statusCode()).isEqualTo(401);
    }
}
