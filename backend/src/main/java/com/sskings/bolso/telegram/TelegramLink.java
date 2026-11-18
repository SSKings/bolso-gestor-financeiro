package com.sskings.bolso.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Conversa privada do Telegram ligada a uma pessoa, com o espaço e a conta onde os lançamentos caem. */
@Entity
@Table(name = "telegram_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TelegramLink {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private long chatId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** Versão dos tokens do usuário ao ligar: se mudar (sair de todos os dispositivos, troca de senha), o vínculo cai. */
    @Column(name = "token_version", nullable = false, updatable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "last_reminder_on")
    private LocalDate lastReminderOn;

    public TelegramLink(UUID userId, long chatId, UUID workspaceId, UUID accountId, int tokenVersion, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.chatId = chatId;
        this.workspaceId = workspaceId;
        this.accountId = accountId;
        this.tokenVersion = tokenVersion;
        this.createdAt = createdAt;
    }
}
