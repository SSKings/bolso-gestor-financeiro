package com.sskings.bolso.rule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByWorkspaceIdOrderByCreatedAt(UUID workspaceId);

    Optional<Rule> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNormalized(UUID workspaceId, String normalized);
}
