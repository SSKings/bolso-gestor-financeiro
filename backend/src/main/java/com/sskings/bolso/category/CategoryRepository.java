package com.sskings.bolso.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toda busca leva o workspaceId: não existe "buscar categoria só pelo id". */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByWorkspaceIdOrderByKindAscNameAsc(UUID workspaceId);

    Optional<Category> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndKindAndNameIgnoreCase(UUID workspaceId, CategoryKind kind, String name);
}
