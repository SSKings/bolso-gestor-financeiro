package com.sskings.bolso.recurrence;

import com.sskings.bolso.account.Account;
import com.sskings.bolso.account.AccountRepository;
import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryKind;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.common.Today;
import com.sskings.bolso.recurrence.RecurrenceDtos.*;
import com.sskings.bolso.security.CurrentUser;
import com.sskings.bolso.transaction.Transaction;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.transaction.TransactionDtos.Ref;
import com.sskings.bolso.transaction.TransactionDtos.TransactionView;
import com.sskings.bolso.transaction.TransactionRepository;
import com.sskings.bolso.transaction.TransactionService;
import com.sskings.bolso.transaction.TransactionType;
import com.sskings.bolso.workspace.WorkspaceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contas fixas e assinaturas. A cada mês, a ocorrência fica pendente até a pessoa confirmar com o valor real; a
 * confirmação cria um lançamento ligado à conta fixa e ao mês (uma só por mês: índice único no banco).
 */
@Service
@RequiredArgsConstructor
public class RecurrenceService {

    /** "Vence em breve": nos próximos 7 dias. */
    static final int DUE_SOON_DAYS = 7;

    private final RecurrenceRepository recurrences;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final WorkspaceAccess access;
    private final Today today;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RecurrenceView> list(UUID ws) {
        access.member(ws);
        Refs refs = refs(ws);
        return recurrences.findByWorkspaceIdOrderByDayOfMonthAscDescriptionAsc(ws).stream().map(r -> view(r, refs)).toList();
    }

    @Transactional
    public RecurrenceView create(UUID ws, RecurrenceRequest req) {
        access.member(ws);
        Recurrence r = new Recurrence(ws, CurrentUser.get().getId(), clock.instant());
        apply(ws, r, req, true);
        return view(recurrences.save(r), refs(ws));
    }

    @Transactional
    public RecurrenceView update(UUID ws, UUID id, RecurrenceRequest req) {
        access.member(ws);
        Recurrence r = find(ws, id);
        apply(ws, r, req, false);
        r.setUpdatedAt(clock.instant());
        return view(r, refs(ws));
    }

    @Transactional
    public void delete(UUID ws, UUID id) {
        access.member(ws);
        recurrences.delete(find(ws, id));   // lançamentos já confirmados ficam (o banco só tira o vínculo)
    }

    private Recurrence find(UUID ws, UUID id) {
        return recurrences.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.notFound("Recurrence not found"));
    }

    private void apply(UUID ws, Recurrence r, RecurrenceRequest req, boolean isNew) {
        if (req.type() != TransactionType.INCOME && req.type() != TransactionType.EXPENSE) {
            throw ApiException.badRequest("Recurrences are incomes or expenses");
        }
        if (req.startMonth().isBefore(YearMonth.from(TransactionService.MIN_DATE))) throw ApiException.badRequest("Date out of range");
        if (req.endMonth() != null && req.endMonth().isBefore(req.startMonth())) {
            throw ApiException.badRequest("End month must not be before the start month");
        }
        Category c = categories.findByIdAndWorkspaceId(req.categoryId(), ws).orElseThrow(() -> ApiException.badRequest("Category not found"));
        CategoryKind expected = req.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (c.getKind() != expected) throw ApiException.badRequest("Category does not match the transaction type");
        if (c.isArchived() && (isNew || !c.getId().equals(r.getCategoryId()))) throw ApiException.badRequest("Category is archived");
        Account a = accounts.findByIdAndWorkspaceId(req.accountId(), ws).orElseThrow(() -> ApiException.badRequest("Account not found"));
        if (a.isArchived() && (isNew || !a.getId().equals(r.getAccountId()))) throw ApiException.badRequest("Account is archived");
        r.setType(req.type());
        r.setDescription(Texts.clean(req.description()));
        r.setAmount(req.amount());
        r.setDayOfMonth(req.dayOfMonth());
        r.setCategoryId(c.getId());
        r.setAccountId(a.getId());
        r.setStartMonth(req.startMonth().atDay(1));
        r.setEndMonth(req.endMonth() == null ? null : req.endMonth().atDay(1));
        if (req.active() != null) r.setActive(req.active());
    }

    /** Ocorrências do mês das contas fixas ativas e no período, com o status de cada uma (por vencimento). */
    @Transactional(readOnly = true)
    public List<Occurrence> occurrences(UUID ws, YearMonth month) {
        access.member(ws);
        Refs refs = refs(ws);
        Map<UUID, Transaction> paid = transactions.findByWorkspaceIdAndRecurrenceMonthAndRecurrenceIdIsNotNull(ws, month.atDay(1)).stream()
                .collect(Collectors.toMap(Transaction::getRecurrenceId, Function.identity()));
        LocalDate now = today.date();
        return recurrences.findByWorkspaceIdOrderByDayOfMonthAscDescriptionAsc(ws).stream()
                .filter(r -> r.scheduledIn(month) || paid.containsKey(r.getId()))
                .map(r -> {
                    LocalDate due = month.atDay(r.getDayOfMonth());
                    Transaction t = paid.get(r.getId());
                    OccurrenceStatus status = t != null ? OccurrenceStatus.PAID
                            : due.isBefore(now) ? OccurrenceStatus.OVERDUE
                            : !due.isAfter(now.plusDays(DUE_SOON_DAYS)) ? OccurrenceStatus.DUE_SOON
                            : OccurrenceStatus.PENDING;
                    RecurrenceView v = view(r, refs);
                    return new Occurrence(r.getId(), r.getType(), r.getDescription(), month, due, r.getAmount(), status,
                            t == null ? null : t.getId(), t == null ? null : t.getAmount(), v.category(), v.account());
                })
                .sorted(Comparator.comparing(Occurrence::dueDate).thenComparing(Occurrence::description))
                .toList();
    }

    @Transactional
    public TransactionView confirm(UUID ws, UUID id, YearMonth month, ConfirmRequest req) {
        access.member(ws);
        Recurrence r = find(ws, id);
        if (!r.isActive()) throw ApiException.badRequest("Recurrence is paused");
        if (!r.withinPeriod(month)) throw ApiException.badRequest("Not scheduled for this month");
        if (transactions.existsByRecurrenceIdAndRecurrenceMonth(r.getId(), month.atDay(1))) {
            throw ApiException.conflict("Already confirmed for this month");
        }
        transactionService.checkDate(req.date());
        Account account = transactionService.usableAccount(ws, req.accountId());
        Transaction t = new Transaction(ws, CurrentUser.get().getId(), clock.instant());
        t.setType(r.getType());
        t.setAmount(req.amount());
        t.setOccurredOn(req.date());
        t.setDescription(r.getDescription());
        t.setAccountId(account.getId());
        t.setCategoryId(r.getCategoryId());
        t.setRecurrenceId(r.getId());
        t.setRecurrenceMonth(month.atDay(1));
        // o índice único do banco segura cliques simultâneos: o segundo vira 409
        return transactionService.view(ws, transactions.saveAndFlush(t));
    }

    private record Refs(Map<UUID, Category> categories, Map<UUID, Account> accounts) {}

    private Refs refs(UUID ws) {
        return new Refs(
                categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream().collect(Collectors.toMap(Category::getId, Function.identity())),
                accounts.findByWorkspaceIdOrderByName(ws).stream().collect(Collectors.toMap(Account::getId, Function.identity())));
    }

    private static RecurrenceView view(Recurrence r, Refs refs) {
        Category c = refs.categories().get(r.getCategoryId());
        Account a = refs.accounts().get(r.getAccountId());
        return new RecurrenceView(r.getId(), r.getType(), r.getDescription(), r.getAmount(), r.getDayOfMonth(),
                new CategoryRef(c.getId(), c.getName(), c.getColor()), new Ref(a.getId(), a.getName()),
                YearMonth.from(r.getStartMonth()), r.getEndMonth() == null ? null : YearMonth.from(r.getEndMonth()), r.isActive());
    }
}
