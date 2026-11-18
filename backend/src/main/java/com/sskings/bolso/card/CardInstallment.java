package com.sskings.bolso.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Parcela de uma compra: valor e a fatura (mês, guardado como dia 1) em que cai. */
@Entity
@Table(name = "card_installments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardInstallment {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "purchase_id", nullable = false)
    private UUID purchaseId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "statement_month", nullable = false)
    private LocalDate statementMonth;

    public CardInstallment(CardPurchase p, int number, BigDecimal amount, LocalDate statementMonth) {
        this.id = UUID.randomUUID();
        this.workspaceId = p.getWorkspaceId();
        this.purchaseId = p.getId();
        this.cardId = p.getCardId();
        this.number = number;
        this.amount = amount;
        this.statementMonth = statementMonth;
    }
}
