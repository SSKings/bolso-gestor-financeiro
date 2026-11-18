package com.sskings.bolso.telegram;

import com.sskings.bolso.account.AccountDtos.AccountView;
import com.sskings.bolso.account.AccountService;
import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryKind;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.common.Today;
import com.sskings.bolso.importing.StatementParser;
import com.sskings.bolso.recurrence.RecurrenceDtos.Occurrence;
import com.sskings.bolso.recurrence.RecurrenceDtos.OccurrenceStatus;
import com.sskings.bolso.recurrence.RecurrenceService;
import com.sskings.bolso.rule.RuleService;
import com.sskings.bolso.transaction.Transaction;
import com.sskings.bolso.transaction.TransactionDtos.TransactionRequest;
import com.sskings.bolso.transaction.TransactionDtos.TransactionView;
import com.sskings.bolso.transaction.TransactionRepository;
import com.sskings.bolso.transaction.TransactionService;
import com.sskings.bolso.transaction.TransactionSource;
import com.sskings.bolso.transaction.TransactionType;
import com.sskings.bolso.user.User;
import com.sskings.bolso.user.UserRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.sskings.bolso.telegram.BotText.brl;
import static com.sskings.bolso.telegram.BotText.esc;

/**
 * Comandos do bot. Tudo que lê ou grava dados roda como o dono da conversa ({@link RunAs}): os serviços do app
 * conferem a participação no espaço como numa requisição HTTP, e os lançamentos passam pela mesma validação.
 * Devolve a resposta em HTML (texto do usuário sempre escapado) ou null quando não há o que responder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCommands {

    static final int MAX_TEXT = 300;
    static final Duration UNDO_WINDOW = Duration.ofHours(24);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final TelegramLinkService linkService;
    private final TelegramLinkRepository links;
    private final UserRepository users;
    private final TransactionService transactionService;
    private final TransactionRepository transactions;
    private final AccountService accounts;
    private final RecurrenceService recurrences;
    private final CategoryRepository categories;
    private final RuleService rules;
    private final Validator validator;
    private final Today today;
    private final Clock clock;

    static final String HELP = """
            <b>Como usar</b>
            • <code>32,50 almoço</code> — lança uma despesa
            • <code>/receita 3500 salário</code> — lança uma receita
            • /saldo — saldo das contas
            • /contas — contas fixas atrasadas ou vencendo
            • /desfazer — apaga o último lançamento feito aqui
            • /sair — desliga esta conversa""";

    public String handle(long chatId, boolean privateChat, String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        if (!privateChat) return "🔒 Por segurança, o Bolso só funciona em conversa privada com o bot.";
        String text = rawText.strip();
        if (text.length() > MAX_TEXT) return "Mensagem longa demais.";
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].startsWith("/") ? parts[0].replaceFirst("@.*$", "").toLowerCase(Locale.ROOT) : null;
        String arg = parts.length > 1 ? parts[1].strip() : "";

        if ("/start".equals(command) && !arg.isEmpty()) return link(chatId, arg);

        Optional<TelegramLink> found = links.findByChatId(chatId);
        if (found.isEmpty()) {
            return "Olá! Para usar o Bolso aqui, ligue esta conversa à sua conta: no app, abra <b>Perfil → Telegram</b> e toque em <b>Ligar</b>.";
        }
        TelegramLink link = found.get();
        User user = users.findById(link.getUserId()).orElse(null);
        if (user == null || !linkService.valid(user, link)) {
            links.delete(link);
            return "Esta conversa foi desligada (você saiu de todos os dispositivos, trocou a senha ou saiu do espaço). Gere um novo código no app.";
        }
        if (command == null) return RunAs.user(user, () -> launch(link, TransactionType.EXPENSE, text));
        return switch (command) {
            case "/start", "/ajuda", "/help" -> HELP;
            case "/receita" -> arg.isEmpty() ? "Use: <code>/receita 3500 salário</code>" : RunAs.user(user, () -> launch(link, TransactionType.INCOME, arg));
            case "/despesa" -> arg.isEmpty() ? "Use: <code>/despesa 32,50 almoço</code>" : RunAs.user(user, () -> launch(link, TransactionType.EXPENSE, arg));
            case "/saldo" -> RunAs.user(user, () -> balance(link));
            case "/contas" -> RunAs.user(user, () -> bills(link));
            case "/desfazer" -> RunAs.user(user, () -> undo(link, user));
            case "/sair" -> {
                links.delete(link);
                yield "Conversa desligada. Para voltar, gere um novo código no app.";
            }
            default -> "Não conheço esse comando.\n\n" + HELP;
        };
    }

    private String link(long chatId, String code) {
        if (!(linkService.consume(chatId, code) instanceof TelegramLinkService.Linked l)) {
            return "Código inválido ou vencido. Gere um novo no app (vale por 10 minutos e só uma vez).";
        }
        log.info("Telegram: conversa ligada (user={}, workspace={})", l.user().getId(), l.link().getWorkspaceId());
        return "✅ Conversa ligada ao espaço <b>" + esc(l.workspaceName()) + "</b>. Os lançamentos vão para a conta <b>"
                + esc(l.accountName()) + "</b>.\n\n" + HELP;
    }

    /** "32,50 almoço", "almoço 32,50" ou "R$ 1.234,56 aluguel": o valor é a primeira ou a última palavra. */
    private String launch(TelegramLink link, TransactionType type, String text) {
        List<String> words = Arrays.asList(text.replace("R$", " ").strip().split("\\s+"));
        BigDecimal amount = null;
        String description = "";
        if (!words.isEmpty() && positive(words.get(0)) != null) {
            amount = positive(words.get(0));
            description = String.join(" ", words.subList(1, words.size()));
        } else if (words.size() > 1 && positive(words.get(words.size() - 1)) != null) {
            amount = positive(words.get(words.size() - 1));
            description = String.join(" ", words.subList(0, words.size() - 1));
        }
        if (amount == null) return "Não entendi o valor. Exemplo: <code>32,50 almoço</code>\n\n/ajuda";
        description = Texts.clean(description.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", ""));
        if (description.isEmpty()) description = type == TransactionType.INCOME ? "Receita pelo Telegram" : "Despesa pelo Telegram";
        if (description.length() > 140) description = description.substring(0, 140).strip();

        CategoryKind kind = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        Optional<Category> category = rules.matcher(link.getWorkspaceId()).match(description).filter(c -> c.getKind() == kind)
                .or(() -> categories.findByWorkspaceIdOrderByKindAscNameAsc(link.getWorkspaceId()).stream()
                        .filter(c -> c.getKind() == kind && !c.isArchived() && c.getName().equals("Outros")).findFirst());
        if (category.isEmpty()) return "Não achei uma categoria. Crie uma regra ou a categoria <b>Outros</b> no app.";

        TransactionRequest req = new TransactionRequest(type, amount, today.date(), description, link.getAccountId(), null, category.get().getId());
        if (!validator.validate(req).isEmpty()) return "Valor ou descrição inválidos.";
        TransactionView t;
        try {
            t = transactionService.create(link.getWorkspaceId(), req, TransactionSource.TELEGRAM);
        } catch (ApiException e) {
            return switch (e.getMessage()) {
                case "Account is archived", "Account not found" -> "A conta desta conversa foi arquivada ou excluída. Gere um novo código no app.";
                default -> "Não consegui lançar.";
            };
        }
        return "✅ " + (type == TransactionType.INCOME ? "Receita" : "Despesa") + " de <b>" + brl(t.amount()) + "</b> — " + esc(t.description())
                + "\n" + esc(t.category().name()) + " · " + esc(t.account().name()) + "\n/desfazer para apagar";
    }

    /**
     * Valor digitado no chat, no formato brasileiro: vírgula é decimal. "10,555" ou "1,234" são recusados (no extrato
     * seriam milhar em inglês; no chat, quase sempre é erro de digitação — e virariam um lançamento mil vezes maior).
     */
    static BigDecimal positive(String word) {
        if (!word.contains(".") && word.matches(".*,[0-9]{3}\\)?-?$")) return null;
        BigDecimal v = StatementParser.amount(word);
        return v != null && v.signum() > 0 ? v : null;
    }

    private String balance(TelegramLink link) {
        List<AccountView> list = accounts.list(link.getWorkspaceId()).stream().filter(a -> !a.archived()).toList();
        if (list.isEmpty()) return "Nenhuma conta ativa.";
        StringBuilder b = new StringBuilder("<b>Saldos</b>\n");
        BigDecimal total = BigDecimal.ZERO;
        for (AccountView a : list) {
            b.append("• ").append(esc(a.name())).append(": ").append(brl(a.balance())).append('\n');
            total = total.add(a.balance());
        }
        return b.append("<b>Total: ").append(brl(total)).append("</b>").toString();
    }

    private String bills(TelegramLink link) {
        String text = billsText(recurrences.occurrences(link.getWorkspaceId(), YearMonth.from(today.date())), false);
        return text == null ? "Nenhuma conta fixa atrasada ou vencendo nos próximos 7 dias. 👍" : text;
    }

    /** Lista das contas atrasadas e vencendo; {@code onlyUrgent} = só atrasadas, de hoje e de amanhã (lembrete). */
    String billsText(List<Occurrence> occurrences, boolean onlyUrgent) {
        var now = today.date();
        List<Occurrence> due = occurrences.stream()
                .filter(o -> o.status() == OccurrenceStatus.OVERDUE
                        || (o.status() == OccurrenceStatus.DUE_SOON && (!onlyUrgent || !o.dueDate().isAfter(now.plusDays(1)))))
                .toList();
        if (due.isEmpty()) return null;
        StringBuilder b = new StringBuilder("<b>Contas fixas</b>\n");
        for (Occurrence o : due) {
            String when = o.status() == OccurrenceStatus.OVERDUE ? "⚠️ atrasada desde " + o.dueDate().format(DAY)
                    : o.dueDate().equals(now) ? "vence hoje" : o.dueDate().equals(now.plusDays(1)) ? "vence amanhã" : "vence " + o.dueDate().format(DAY);
            b.append("• ").append(esc(o.description())).append(" — ").append(brl(o.expectedAmount())).append(" (").append(when).append(")\n");
        }
        return b.append("Confirme o pagamento no app.").toString();
    }

    private String undo(TelegramLink link, User user) {
        Optional<Transaction> last = transactions.findFirstByWorkspaceIdAndCreatedByAndSourceAndCreatedAtAfterOrderByCreatedAtDesc(
                link.getWorkspaceId(), user.getId(), TransactionSource.TELEGRAM, clock.instant().minus(UNDO_WINDOW));
        if (last.isEmpty()) return "Nada para desfazer (só os lançamentos feitos aqui nas últimas 24 horas).";
        Transaction t = last.get();
        transactionService.delete(link.getWorkspaceId(), t.getId());
        return "↩️ Apagado: " + esc(t.getDescription()) + " — " + brl(t.getAmount());
    }

}
