package com.sskings.bolso.recurrence;

import com.sskings.bolso.transaction.TransactionType;
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
import java.time.YearMonth;
import java.util.UUID;

/**
 * Conta fixa ou assinatura (ou receita fixa, como o salário). Não gera lançamento sozinha: a cada mês fica pendente
 * até a pessoa confirmar com o valor real (a conta de luz muda todo mês).
 */
@Entity
@Table(name = "recurrences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recurrence {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "end_month")
    private LocalDate endMonth;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Recurrence(UUID workspaceId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Está agendada para o mês (ativa e dentro do período)? */
    public boolean scheduledIn(YearMonth month) {
        LocalDate m = month.atDay(1);
        return active && !m.isBefore(startMonth) && (endMonth == null || !m.isAfter(endMonth));
    }

    public boolean withinPeriod(YearMonth month) {
        LocalDate m = month.atDay(1);
        return !m.isBefore(startMonth) && (endMonth == null || !m.isAfter(endMonth));
    }
}
