package com.sskings.bolso.rule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** "Descrição contém {pattern} → categoria". Texto simples: nunca é interpretado como regex (sem ReDoS). */
@Entity
@Table(name = "categorization_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rule {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 60)
    private String pattern;

    @Column(nullable = false, length = 60)
    private String normalized;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Rule(UUID workspaceId, String pattern, String normalized, UUID categoryId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.pattern = pattern;
        this.normalized = normalized;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
    }
}
