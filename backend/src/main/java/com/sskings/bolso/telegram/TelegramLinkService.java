package com.sskings.bolso.telegram;

import com.sskings.bolso.account.Account;
import com.sskings.bolso.account.AccountRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Tokens;
import com.sskings.bolso.security.CurrentUser;
import com.sskings.bolso.transaction.TransactionDtos.Ref;
import com.sskings.bolso.transaction.TransactionService;
import com.sskings.bolso.user.User;
import com.sskings.bolso.user.UserRepository;
import com.sskings.bolso.workspace.MembershipRepository;
import com.sskings.bolso.workspace.WorkspaceAccess;
import com.sskings.bolso.workspace.WorkspaceRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Vínculo da conversa do Telegram com a pessoa. O app gera um código de uso único (256 bits, 10 minutos, só o hash
 * no banco); a pessoa manda o código ao bot, numa conversa privada. Um código novo invalida os anteriores.
 */
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final TelegramProperties props;
    private final TelegramLinkRepository links;
    private final TelegramCodeRepository codes;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final WorkspaceRepository workspaces;
    private final AccountRepository accounts;
    private final WorkspaceAccess access;
    private final TransactionService transactions;
    private final Clock clock;

    public record CodeRequest(@NotNull UUID workspaceId, @NotNull UUID accountId) {}

    public record CodeResponse(String code, String link, Instant expiresAt) {}

    public record LinkView(Ref workspace, Ref account, Instant linkedAt) {}

    public record Status(boolean available, String botUsername, LinkView link) {}

    /** Resultado do /start no bot. */
    sealed interface LinkResult permits Linked, Invalid {}

    record Linked(User user, TelegramLink link, String workspaceName, String accountName) implements LinkResult {}

    record Invalid() implements LinkResult {}

    @Transactional(readOnly = true)
    public Status status() {
        User u = CurrentUser.get();
        LinkView view = links.findByUserId(u.getId()).filter(l -> valid(u, l)).map(this::view).orElse(null);
        return new Status(props.enabled(), props.enabled() ? props.botUsername() : null, view);
    }

    @Transactional
    public CodeResponse createCode(CodeRequest req) {
        if (!props.enabled()) throw new ApiException(HttpStatus.CONFLICT, "Telegram is not configured");
        access.member(req.workspaceId());
        Account a = transactions.usableAccount(req.workspaceId(), req.accountId());
        User u = CurrentUser.get();
        codes.deleteByUserId(u.getId());
        String code = Tokens.random();
        Instant now = clock.instant();
        codes.save(new TelegramLinkCode(u.getId(), req.workspaceId(), a.getId(), Tokens.sha256(code), now, now.plus(CODE_TTL)));
        return new CodeResponse(code, "https://t.me/" + props.botUsername() + "?start=" + code, now.plus(CODE_TTL));
    }

    @Transactional
    public void unlink() {
        UUID user = CurrentUser.get().getId();
        links.findByUserId(user).ifPresent(links::delete);
        codes.deleteByUserId(user);
    }

    /**
     * Consome o código: precisa existir, não ter sido usado nem vencido, e a pessoa precisa continuar no espaço e a
     * conta continuar usável. Qualquer falha dá a mesma resposta (não revela se o código existiu).
     */
    @Transactional
    LinkResult consume(long chatId, String code) {
        if (!Tokens.wellFormed(code)) return new Invalid();
        Optional<TelegramLinkCode> found = codes.findByCodeHash(Tokens.sha256(code));
        Instant now = clock.instant();
        if (found.isEmpty() || found.get().getUsedAt() != null || !now.isBefore(found.get().getExpiresAt())) return new Invalid();
        TelegramLinkCode c = found.get();
        c.setUsedAt(now);
        User user = users.findById(c.getUserId()).orElse(null);
        if (user == null || memberships.findByWorkspaceIdAndUserId(c.getWorkspaceId(), user.getId()).isEmpty()) return new Invalid();
        Account account = accounts.findByIdAndWorkspaceId(c.getAccountId(), c.getWorkspaceId()).filter(a -> !a.isArchived()).orElse(null);
        if (account == null) return new Invalid();
        links.deleteByUserIdOrChatId(user.getId(), chatId);      // uma conversa por pessoa e uma pessoa por conversa
        links.flush();
        TelegramLink link = links.save(new TelegramLink(user.getId(), chatId, c.getWorkspaceId(), account.getId(), user.getTokenVersion(), now));
        return new Linked(user, link, workspaces.getReferenceById(c.getWorkspaceId()).getName(), account.getName());
    }

    /** O vínculo só vale enquanto os tokens da pessoa não forem revogados e ela continuar no espaço. */
    boolean valid(User user, TelegramLink link) {
        return user.getTokenVersion() == link.getTokenVersion()
                && memberships.findByWorkspaceIdAndUserId(link.getWorkspaceId(), user.getId()).isPresent();
    }

    private LinkView view(TelegramLink l) {
        return new LinkView(new Ref(l.getWorkspaceId(), workspaces.getReferenceById(l.getWorkspaceId()).getName()),
                new Ref(l.getAccountId(), accounts.getReferenceById(l.getAccountId()).getName()), l.getCreatedAt());
    }
}
