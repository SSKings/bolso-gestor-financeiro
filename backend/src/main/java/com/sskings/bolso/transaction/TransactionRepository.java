package com.sskings.bolso.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/** Toda busca leva o workspaceId: não existe "buscar lançamento só pelo id". */
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select count(t) > 0 from Transaction t where t.workspaceId = :ws and (t.accountId = :account or t.toAccountId = :account)")
    boolean existsByAccount(@Param("ws") UUID workspaceId, @Param("account") UUID accountId);

    boolean existsByWorkspaceIdAndCategoryId(UUID workspaceId, UUID categoryId);

    boolean existsByWorkspaceIdAndCardId(UUID workspaceId, UUID cardId);

    java.util.List<Transaction> findByWorkspaceIdAndRecurrenceMonthAndRecurrenceIdIsNotNull(UUID workspaceId, java.time.LocalDate month);

    boolean existsByRecurrenceIdAndRecurrenceMonth(UUID recurrenceId, java.time.LocalDate month);

    java.util.Optional<Transaction> findFirstByWorkspaceIdAndCreatedByAndSourceAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID workspaceId, UUID createdBy, TransactionSource source, java.time.Instant after);

    @Query("select t.externalId from Transaction t where t.workspaceId = :ws and t.accountId = :account and t.externalId in :ids")
    java.util.Set<String> existingExternalIds(@Param("ws") UUID workspaceId, @Param("account") UUID accountId, @Param("ids") java.util.Collection<String> ids);

    @Query("select t from Transaction t where t.workspaceId = :ws and t.accountId = :account and t.externalId is null and t.occurredOn between :from and :to")
    java.util.List<Transaction> manualInRange(@Param("ws") UUID workspaceId, @Param("account") UUID accountId,
                                              @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    java.util.List<Transaction> findByWorkspaceIdAndCardIdAndStatementMonthOrderByOccurredOn(UUID workspaceId, UUID cardId, java.time.LocalDate month);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.workspaceId = :ws and t.cardId = :card")
    java.math.BigDecimal paymentsByCard(@Param("ws") UUID workspaceId, @Param("card") UUID cardId);
}
