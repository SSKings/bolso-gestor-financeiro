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
import java.time.LocalDate;
import java.util.UUID;

/** Compra no cartão (à vista ou parcelada). As parcelas ficam em CardInstallment, uma por fatura. */
@Entity
@Table(name = "card_purchases")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardPurchase {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int installments;

    @Column(name = "purchased_on", nullable = false)
    private LocalDate purchasedOn;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CardPurchase(UUID workspaceId, UUID cardId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.cardId = cardId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
