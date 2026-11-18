package com.sskings.bolso.recurrence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toda busca leva o workspaceId. */
public interface RecurrenceRepository extends JpaRepository<Recurrence, UUID> {

    List<Recurrence> findByWorkspaceIdOrderByDayOfMonthAscDescriptionAsc(UUID workspaceId);

    Optional<Recurrence> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);

    boolean existsByWorkspaceIdAndAccountId(UUID workspaceId, UUID accountId);
}
