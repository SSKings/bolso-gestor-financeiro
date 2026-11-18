package com.sskings.bolso.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TelegramLinkRepository extends JpaRepository<TelegramLink, UUID> {

    Optional<TelegramLink> findByChatId(long chatId);

    Optional<TelegramLink> findByUserId(UUID userId);

    @Modifying
    @Query("delete from TelegramLink l where l.userId = :user or l.chatId = :chat")
    void deleteByUserIdOrChatId(@Param("user") UUID userId, @Param("chat") long chatId);
}
