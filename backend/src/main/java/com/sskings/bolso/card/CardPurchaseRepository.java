package com.sskings.bolso.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardPurchaseRepository extends JpaRepository<CardPurchase, UUID> {

    /** Compra só é encontrada pelo cartão E pelo espaço da rota. */
    Optional<CardPurchase> findByIdAndCardIdAndWorkspaceId(UUID id, UUID cardId, UUID workspaceId);

    List<CardPurchase> findByIdIn(List<UUID> ids);

    boolean existsByWorkspaceIdAndCardId(UUID workspaceId, UUID cardId);

    boolean existsByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);
}
