package com.sskings.bolso.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CardInstallmentRepository extends JpaRepository<CardInstallment, UUID> {

    List<CardInstallment> findByCardIdAndWorkspaceIdAndStatementMonthOrderByNumber(UUID cardId, UUID workspaceId, LocalDate month);

    List<CardInstallment> findByPurchaseIdOrderByNumber(UUID purchaseId);

    @Query("select coalesce(sum(i.amount), 0) from CardInstallment i where i.cardId = :card and i.workspaceId = :ws")
    BigDecimal totalByCard(@Param("card") UUID cardId, @Param("ws") UUID workspaceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CardInstallment i where i.purchaseId = :purchase")
    void deleteByPurchase(@Param("purchase") UUID purchaseId);
}
