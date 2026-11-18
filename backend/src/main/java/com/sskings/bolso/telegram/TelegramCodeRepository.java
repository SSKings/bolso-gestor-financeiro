package com.sskings.bolso.telegram;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TelegramCodeRepository extends JpaRepository<TelegramLinkCode, UUID> {

    /** Trava a linha: dois "/start" com o mesmo código ao mesmo tempo não ligam duas conversas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TelegramLinkCode> findByCodeHash(String codeHash);

    @Modifying
    @Query("delete from TelegramLinkCode c where c.userId = :user")
    void deleteByUserId(@Param("user") UUID userId);
}
