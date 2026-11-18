package com.sskings.bolso.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lançamento: receita, despesa ou transferência entre contas do mesmo espaço. Valor sempre positivo; o tipo diz o
 * sentido. O banco garante a combinação (transferência sem categoria e com destino; o resto com categoria) e que
 * conta e categoria sejam do mesmo espaço (chaves compostas).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    @Column(name = "category_id")
    private UUID categoryId;

    /** Só em CARD_PAYMENT: o cartão e a fatura (mês, como dia 1) que o pagamento quita. */
    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "statement_month")
    private LocalDate statementMonth;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Transaction(UUID workspaceId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
