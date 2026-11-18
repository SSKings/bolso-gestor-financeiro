package com.sskings.bolso.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toda busca leva o workspaceId: não existe "buscar conta só pelo id". */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByWorkspaceIdOrderByName(UUID workspaceId);

    Optional<Account> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}
