package com.sskings.bolso.telegram;

/** Falha ao falar com a API do Telegram. A mensagem nunca contém o token. */
public class TelegramException extends RuntimeException {
    public TelegramException(String message) {
        super(message);
    }
}
