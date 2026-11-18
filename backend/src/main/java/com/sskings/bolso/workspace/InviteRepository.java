package com.sskings.bolso.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(String tokenHash);

    Optional<Invite> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select i from Invite i where i.workspaceId = :ws and i.usedAt is null and i.expiresAt > :now order by i.createdAt")
    List<Invite> findPending(@Param("ws") UUID workspaceId, @Param("now") Instant now);

    /** Gasta o convite só se ainda valer: dois aceites simultâneos do mesmo código não valem os dois. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Invite i set i.usedAt = :now, i.usedBy = :user where i.id = :id and i.usedAt is null and i.expiresAt > :now")
    int markUsed(@Param("id") UUID id, @Param("user") UUID userId, @Param("now") Instant now);
}
