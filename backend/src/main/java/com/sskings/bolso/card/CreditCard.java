package com.sskings.bolso.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cartão de crédito. Só os 4 últimos dígitos — o número completo nunca entra no sistema. */
@Entity
@Table(name = "credit_cards")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditCard {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 4)
    private String last4;

    @Column(name = "closing_day", nullable = false)
    private int closingDay;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CreditCard(UUID workspaceId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.createdAt = createdAt;
    }
}
