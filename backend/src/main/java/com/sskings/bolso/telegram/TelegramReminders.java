package com.sskings.bolso.telegram;

import com.sskings.bolso.common.Today;
import com.sskings.bolso.recurrence.RecurrenceService;
import com.sskings.bolso.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lembrete diário das contas fixas atrasadas, de hoje e de amanhã. No máximo um por dia por conversa e só quando há
 * algo a avisar. Vínculo que deixou de valer (tokens revogados, saiu do espaço) é apagado aqui.
 */
@Service
@RequiredArgsConstructor
public class TelegramReminders {

    private final TelegramLinkRepository links;
    private final TelegramLinkService linkService;
    private final com.sskings.bolso.user.UserRepository users;
    private final RecurrenceService recurrences;
    private final TelegramCommands commands;
    private final Today today;

    public record Reminder(UUID linkId, long chatId, String text) {}

    /** Lembretes de hoje ainda não enviados. As conversas sem nada a avisar já ficam marcadas como resolvidas. */
    @Transactional
    public List<Reminder> pending() {
        var now = today.date();
        List<Reminder> out = new ArrayList<>();
        for (TelegramLink link : links.findAll()) {
            if (now.equals(link.getLastReminderOn())) continue;
            User user = users.findById(link.getUserId()).orElse(null);
            if (user == null || !linkService.valid(user, link)) {
                links.delete(link);
                continue;
            }
            String text = RunAs.user(user, () -> commands.billsText(recurrences.occurrences(link.getWorkspaceId(), YearMonth.from(now)), true));
            if (text == null) link.setLastReminderOn(now);
            else out.add(new Reminder(link.getId(), link.getChatId(), "🔔 " + text));
        }
        return out;
    }

    /** Marca depois de enviar: se o envio falhar, a próxima rodada tenta de novo. */
    @Transactional
    public void markSent(UUID linkId) {
        links.findById(linkId).ifPresent(l -> l.setLastReminderOn(today.date()));
    }
}
