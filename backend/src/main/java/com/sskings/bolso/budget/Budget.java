package com.sskings.bolso.budget;

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

/** Limite mensal de gasto numa categoria de despesa (vale para todos os meses). */
@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Budget(UUID workspaceId, UUID categoryId, Instant now) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.categoryId = categoryId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
