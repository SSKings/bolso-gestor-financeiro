package com.sskings.bolso.workspace;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, Membership.Key> {

    Optional<Membership> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<Membership> findByUserIdOrderByJoinedAt(UUID userId);

    List<Membership> findByWorkspaceIdOrderByJoinedAt(UUID workspaceId);

    /** Trava os membros do espaço: duas saídas simultâneas não podem deixar o espaço sem dono. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.workspaceId = :workspaceId")
    List<Membership> lockAll(@Param("workspaceId") UUID workspaceId);
}
