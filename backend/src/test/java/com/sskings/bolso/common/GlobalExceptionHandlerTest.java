package com.sskings.bolso.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Erro inesperado → 500 genérico: nada da exceção (mensagem, classe, SQL) chega ao cliente")
    void unexpectedDoesNotLeak() {
        var r = handler.unexpected(new IllegalStateException("SELECT * FROM users WHERE senha = 'x' at com.sskings.Foo"));
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        assertThat(r.getBody().message()).isEqualTo("Internal error");
        assertThat(r.getBody().toString()).doesNotContain("SELECT", "IllegalState", "com.sskings", "senha");
    }

    @Test
    @DisplayName("Upload: acima do tamanho → 413; multipart quebrado → 400, sem detalhes")
    void uploads() {
        assertThat(handler.uploadTooLarge(new MaxUploadSizeExceededException(1)).getStatusCode().value()).isEqualTo(413);
        var bad = handler.badUpload(new MultipartException("Failed to parse multipart servlet request; boundary=xyz"));
        assertThat(bad.getStatusCode().value()).isEqualTo(400);
        assertThat(bad.getBody().message()).isEqualTo("Invalid upload");
    }
}
