package com.sskings.bolso.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Espaço: onde ficam contas, categorias e lançamentos. Pessoal (um membro) ou compartilhado (casal, família). */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Workspace(String name, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.createdAt = createdAt;
    }
}
