package com.sskings.bolso.transaction;

import com.sskings.bolso.account.Account;
import com.sskings.bolso.account.AccountRepository;
import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryKind;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.common.Today;
import com.sskings.bolso.security.CurrentUser;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.transaction.TransactionDtos.Page;
import com.sskings.bolso.transaction.TransactionDtos.Ref;
import com.sskings.bolso.transaction.TransactionDtos.TransactionRequest;
import com.sskings.bolso.transaction.TransactionDtos.TransactionView;
import com.sskings.bolso.user.User;
import com.sskings.bolso.user.UserRepository;
import com.sskings.bolso.workspace.WorkspaceAccess;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lançamentos. Conta, conta de destino e categoria são buscadas SEMPRE dentro do espaço da rota: id de outro espaço
 * é "não encontrado" (e o banco recusaria de qualquer jeito, pelas chaves compostas).
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final com.sskings.bolso.card.CreditCardRepository cards;
    private final WorkspaceAccess access;
    private final Today today;
    private final Clock clock;

    public record Filter(YearMonth month, UUID accountId, UUID categoryId, TransactionType type, String q) {}

    @Transactional
    public TransactionView create(UUID ws, TransactionRequest req) {
        access.member(ws);
        Transaction t = new Transaction(ws, CurrentUser.get().getId(), clock.instant());
        apply(ws, t, req, true);
        return views(ws, List.of(transactions.save(t))).get(0);
    }

    @Transactional
    public TransactionView update(UUID ws, UUID id, TransactionRequest req) {
        access.member(ws);
        Transaction t = find(ws, id);
        if (t.getType() == TransactionType.CARD_PAYMENT) {
            throw ApiException.badRequest("Card payments are changed through the card statement");
        }
        apply(ws, t, req, false);
        t.setUpdatedAt(clock.instant());
        return views(ws, List.of(t)).get(0);
    }

    @Transactional
    public void delete(UUID ws, UUID id) {
        access.member(ws);
        transactions.delete(find(ws, id));
    }

    @Transactional(readOnly = true)
    public TransactionView get(UUID ws, UUID id) {
        access.member(ws);
        return views(ws, List.of(find(ws, id))).get(0);
    }

    @Transactional(readOnly = true)
    public Page list(UUID ws, Filter f, int page, int size) {
        access.member(ws);
        var result = transactions.findAll(spec(ws, f),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("createdAt"))));
        return new Page(views(ws, result.getContent()), result.getTotalElements(), page, size);
    }

    private Transaction find(UUID ws, UUID id) {
        return transactions.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.notFound("Transaction not found"));
    }

    /** Valida a combinação e grava os campos. Conta/categoria arquivadas não recebem lançamento NOVO (nem trocado para elas). */
    private void apply(UUID ws, Transaction t, TransactionRequest req, boolean isNew) {
        if (req.type() == TransactionType.CARD_PAYMENT) {
            throw ApiException.badRequest("Card payments are made through the card statement");
        }
        checkDate(req.date());

        Account account = usable(ws, req.accountId(), isNew || !req.accountId().equals(t.getAccountId()));
        UUID toAccount = null;
        UUID category = null;
        if (req.type() == TransactionType.TRANSFER) {
            if (req.toAccountId() == null) throw ApiException.badRequest("Transfer needs a destination account");
            if (req.categoryId() != null) throw ApiException.badRequest("Transfer has no category");
            if (req.toAccountId().equals(account.getId())) throw ApiException.badRequest("Destination must be a different account");
            toAccount = usable(ws, req.toAccountId(), isNew || !req.toAccountId().equals(t.getToAccountId())).getId();
        } else {
            if (req.toAccountId() != null) throw ApiException.badRequest("Only transfers have a destination account");
            if (req.categoryId() == null) throw ApiException.badRequest("Category is required");
            Category c = categories.findByIdAndWorkspaceId(req.categoryId(), ws)
                    .orElseThrow(() -> ApiException.badRequest("Category not found"));
            if (c.isArchived() && (isNew || !c.getId().equals(t.getCategoryId()))) throw ApiException.badRequest("Category is archived");
            CategoryKind expected = req.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
            if (c.getKind() != expected) throw ApiException.badRequest("Category does not match the transaction type");
            category = c.getId();
        }
        t.setType(req.type());
        t.setAmount(req.amount());
        t.setOccurredOn(req.date());
        t.setDescription(Texts.clean(req.description()));
        t.setAccountId(account.getId());
        t.setToAccountId(toAccount);
        t.setCategoryId(category);
    }

    /** Conta do espaço, ativa (também usada pelo pagamento de fatura). */
    public Account usableAccount(UUID ws, UUID id) {
        return usable(ws, id, true);
    }

    /** Data aceita em lançamentos: de 2000 a um ano à frente. */
    public void checkDate(LocalDate date) {
        if (date.isBefore(MIN_DATE) || date.isAfter(today.date().plusYears(1))) throw ApiException.badRequest("Date out of range");
    }

    public TransactionView view(UUID ws, Transaction t) {
        return views(ws, List.of(t)).get(0);
    }

    private Account usable(UUID ws, UUID id, boolean checkArchived) {
        Account a = accounts.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.badRequest("Account not found"));
        if (checkArchived && a.isArchived()) throw ApiException.badRequest("Account is archived");
        return a;
    }

    static Specification<Transaction> spec(UUID ws, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("workspaceId"), ws));
            if (f.month() != null) p.add(cb.between(root.get("occurredOn"), f.month().atDay(1), f.month().atEndOfMonth()));
            if (f.accountId() != null) {
                p.add(cb.or(cb.equal(root.get("accountId"), f.accountId()), cb.equal(root.get("toAccountId"), f.accountId())));
            }
            if (f.categoryId() != null) p.add(cb.equal(root.get("categoryId"), f.categoryId()));
            if (f.type() != null) p.add(cb.equal(root.get("type"), f.type()));
            if (f.q() != null && !f.q().isBlank()) {
                p.add(cb.like(cb.lower(root.get("description")), "%" + escapeLike(f.q().strip().toLowerCase()) + "%", '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** "%" e "_" digitados na busca são texto, não curinga. */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<TransactionView> views(UUID ws, Collection<Transaction> list) {
        Map<UUID, Account> acc = accounts.findByWorkspaceIdOrderByName(ws).stream().collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<UUID, Category> cat = categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<UUID, com.sskings.bolso.card.CreditCard> crd = list.stream().anyMatch(t -> t.getCardId() != null)
                ? cards.findByWorkspaceIdOrderByName(ws).stream().collect(Collectors.toMap(com.sskings.bolso.card.CreditCard::getId, Function.identity()))
                : Map.of();
        Map<UUID, User> usr = users.findAllById(list.stream().map(Transaction::getCreatedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(t -> {
            Account a = acc.get(t.getAccountId());
            Account to = t.getToAccountId() == null ? null : acc.get(t.getToAccountId());
            Category c = t.getCategoryId() == null ? null : cat.get(t.getCategoryId());
            User u = t.getCreatedBy() == null ? null : usr.get(t.getCreatedBy());
            com.sskings.bolso.card.CreditCard card = t.getCardId() == null ? null : crd.get(t.getCardId());
            return new TransactionView(t.getId(), t.getType(), t.getAmount(), t.getOccurredOn(), t.getDescription(),
                    new Ref(a.getId(), a.getName()), to == null ? null : new Ref(to.getId(), to.getName()),
                    c == null ? null : new CategoryRef(c.getId(), c.getName(), c.getColor()),
                    card == null ? null : new Ref(card.getId(), card.getName()),
                    t.getStatementMonth() == null ? null : YearMonth.from(t.getStatementMonth()),
                    u == null ? null : new Ref(u.getId(), u.getName()), t.getCreatedAt());
        }).toList();
    }
}
