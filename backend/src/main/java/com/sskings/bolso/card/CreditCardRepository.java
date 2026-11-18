package com.sskings.bolso.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toda busca leva o workspaceId: não existe "buscar cartão só pelo id". */
public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    List<CreditCard> findByWorkspaceIdOrderByName(UUID workspaceId);

    Optional<CreditCard> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}
