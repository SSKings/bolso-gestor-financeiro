package com.sskings.bolso.card;

import com.sskings.bolso.account.Account;
import com.sskings.bolso.card.CardDtos.*;
import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryKind;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.common.Today;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cartão de crédito: cadastro, compras (com parcelas nas faturas certas), faturas e pagamento.
 * <ul>
 *   <li>Cartão, compra, conta e categoria são buscados SEMPRE dentro do espaço da rota (outro espaço = inexistente);
 *       o banco garante o mesmo com chaves compostas.</li>
 *   <li>Pagar a fatura cria um lançamento CARD_PAYMENT: sai da conta, mas não é gasto (o gasto está nas parcelas).</li>
 *   <li>Limite disponível = limite − (parcelas lançadas − pagamentos).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CardService {

    static final String NOT_FOUND = "Card not found";

    private final CreditCardRepository cards;
    private final CardPurchaseRepository purchases;
    private final CardInstallmentRepository installments;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;
    private final WorkspaceAccess access;
    private final Today today;
    private final Clock clock;

    // ---------- cartões ----------

    @Transactional(readOnly = true)
    public List<CardView> list(UUID ws) {
        access.member(ws);
        return cards.findByWorkspaceIdOrderByName(ws).stream().map(c -> view(ws, c)).toList();
    }

    @Transactional(readOnly = true)
    public CardView get(UUID ws, UUID id) {
        access.member(ws);
        return view(ws, find(ws, id));
    }

    @Transactional
    public CardView create(UUID ws, CardRequest req) {
        access.member(ws);
        String name = Texts.clean(req.name());
        if (cards.existsByWorkspaceIdAndNameIgnoreCase(ws, name)) throw ApiException.conflict("Card name already in use");
        CreditCard c = new CreditCard(ws, clock.instant());
        fill(c, req, name);
        return view(ws, cards.saveAndFlush(c));
    }

    @Transactional
    public CardView update(UUID ws, UUID id, CardRequest req) {
        access.member(ws);
        CreditCard c = find(ws, id);
        String name = Texts.clean(req.name());
        if (!c.getName().equalsIgnoreCase(name) && cards.existsByWorkspaceIdAndNameIgnoreCase(ws, name)) {
            throw ApiException.conflict("Card name already in use");
        }
        fill(c, req, name);
        return view(ws, c);
    }

    @Transactional
    public void delete(UUID ws, UUID id) {
        access.member(ws);
        CreditCard c = find(ws, id);
        if (purchases.existsByWorkspaceIdAndCardId(ws, id) || transactions.existsByWorkspaceIdAndCardId(ws, id)) {
            throw ApiException.conflict("Card has purchases; archive it instead");
        }
        cards.delete(c);
    }

    private static void fill(CreditCard c, CardRequest req, String name) {
        c.setName(name);
        c.setLast4(req.last4());
        c.setClosingDay(req.closingDay());
        c.setDueDay(req.dueDay());
        c.setCreditLimit(req.creditLimit());
        if (req.archived() != null) c.setArchived(req.archived());
    }

    private CreditCard find(UUID ws, UUID id) {
        return cards.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private CardView view(UUID ws, CreditCard c) {
        BigDecimal committed = installments.totalByCard(c.getId(), ws).subtract(transactions.paymentsByCard(ws, c.getId()));
        return new CardView(c.getId(), c.getName(), c.getLast4(), c.getClosingDay(), c.getDueDay(), c.getCreditLimit(),
                c.getCreditLimit().subtract(committed), c.isArchived());
    }

    // ---------- compras ----------

    @Transactional
    public PurchaseView buy(UUID ws, UUID cardId, PurchaseRequest req) {
        access.member(ws);
        CreditCard card = find(ws, cardId);
        if (card.isArchived()) throw ApiException.badRequest("Card is archived");
        CardPurchase p = new CardPurchase(ws, card.getId(), CurrentUser.get().getId(), clock.instant());
        return apply(ws, card, p, req, true);
    }

    @Transactional
    public PurchaseView updatePurchase(UUID ws, UUID cardId, UUID purchaseId, PurchaseRequest req) {
        access.member(ws);
        CreditCard card = find(ws, cardId);
        CardPurchase p = findPurchase(ws, cardId, purchaseId);
        installments.deleteByPurchase(p.getId());
        p.setUpdatedAt(clock.instant());
        return apply(ws, card, p, req, false);
    }

    @Transactional
    public void deletePurchase(UUID ws, UUID cardId, UUID purchaseId) {
        access.member(ws);
        find(ws, cardId);
        purchases.delete(findPurchase(ws, cardId, purchaseId));   // parcelas saem em cascata
    }

    private CardPurchase findPurchase(UUID ws, UUID cardId, UUID id) {
        return purchases.findByIdAndCardIdAndWorkspaceId(id, cardId, ws).orElseThrow(() -> ApiException.notFound("Purchase not found"));
    }

    private PurchaseView apply(UUID ws, CreditCard card, CardPurchase p, PurchaseRequest req, boolean isNew) {
        transactionService.checkDate(req.date());
        Category c = categories.findByIdAndWorkspaceId(req.categoryId(), ws).orElseThrow(() -> ApiException.badRequest("Category not found"));
        if (c.getKind() != CategoryKind.EXPENSE) throw ApiException.badRequest("Category does not match the transaction type");
        if (c.isArchived() && (isNew || !c.getId().equals(p.getCategoryId()))) throw ApiException.badRequest("Category is archived");
        List<BigDecimal> parts;
        try {
            parts = Billing.split(req.amount(), req.installments());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Each installment must be at least R$ 0,01");
        }
        p.setDescription(Texts.clean(req.description()));
        p.setAmount(req.amount());
        p.setInstallments(req.installments());
        p.setPurchasedOn(req.date());
        p.setCategoryId(c.getId());
        purchases.saveAndFlush(p);
        YearMonth first = Billing.statementFor(req.date(), card.getClosingDay());
        List<CardInstallment> list = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            list.add(new CardInstallment(p, i + 1, parts.get(i), first.plusMonths(i).atDay(1)));
        }
        installments.saveAll(list);
        return new PurchaseView(p.getId(), p.getDescription(), p.getAmount(), p.getInstallments(), p.getPurchasedOn(),
                new CategoryRef(c.getId(), c.getName(), c.getColor()),
                list.stream().map(i -> new Part(i.getNumber(), i.getAmount(), YearMonth.from(i.getStatementMonth()))).toList());
    }

    // ---------- faturas ----------

    @Transactional(readOnly = true)
    public StatementView statement(UUID ws, UUID cardId, YearMonth month) {
        access.member(ws);
        CreditCard card = find(ws, cardId);
        List<CardInstallment> items = installments.findByCardIdAndWorkspaceIdAndStatementMonthOrderByNumber(cardId, ws, month.atDay(1));
        Map<UUID, CardPurchase> byId = purchases.findByIdIn(items.stream().map(CardInstallment::getPurchaseId).distinct().toList())
                .stream().collect(Collectors.toMap(CardPurchase::getId, Function.identity()));
        Map<UUID, Category> cats = categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<StatementItem> rows = items.stream().map(i -> {
            CardPurchase p = byId.get(i.getPurchaseId());
            Category c = cats.get(p.getCategoryId());
            return new StatementItem(p.getId(), p.getDescription(), i.getNumber(), p.getInstallments(), i.getAmount(),
                    p.getPurchasedOn(), new CategoryRef(c.getId(), c.getName(), c.getColor()));
        }).sorted(java.util.Comparator.comparing(StatementItem::purchaseDate).thenComparing(StatementItem::description)).toList();
        List<Transaction> pays = transactions.findByWorkspaceIdAndCardIdAndStatementMonthOrderByOccurredOn(ws, cardId, month.atDay(1));
        List<StatementPayment> payments = pays.stream().map(t -> {
            TransactionView v = transactionService.view(ws, t);
            return new StatementPayment(t.getId(), t.getOccurredOn(), t.getAmount(), v.account());
        }).toList();
        BigDecimal total = rows.stream().map(StatementItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = pays.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate closing = Billing.closingDate(month, card.getClosingDay());
        return new StatementView(month, closing, Billing.dueDate(month, card.getClosingDay(), card.getDueDay()),
                status(total, paid, closing), total, paid, total.subtract(paid).max(BigDecimal.ZERO), rows, payments);
    }

    private StatementStatus status(BigDecimal total, BigDecimal paid, LocalDate closing) {
        if (total.signum() > 0 && paid.compareTo(total) >= 0) return StatementStatus.PAID;
        return today.date().isAfter(closing) ? StatementStatus.CLOSED : StatementStatus.OPEN;
    }

    /** Fatura do mês de cada cartão ativo (para o painel). */
    @Transactional(readOnly = true)
    public List<StatementSummary> summaries(UUID ws, YearMonth month) {
        access.member(ws);
        return cards.findByWorkspaceIdOrderByName(ws).stream().filter(c -> !c.isArchived()).map(c -> {
            StatementView s = statement(ws, c.getId(), month);
            return new StatementSummary(c.getId(), c.getName(), s.total(), s.dueDate(), s.status());
        }).toList();
    }

    @Transactional
    public TransactionView pay(UUID ws, UUID cardId, YearMonth month, PaymentRequest req) {
        access.member(ws);
        CreditCard card = find(ws, cardId);
        transactionService.checkDate(req.date());
        Account account = transactionService.usableAccount(ws, req.accountId());
        Transaction t = new Transaction(ws, CurrentUser.get().getId(), clock.instant());
        t.setType(TransactionType.CARD_PAYMENT);
        t.setAmount(req.amount());
        t.setOccurredOn(req.date());
        t.setDescription("Fatura " + card.getName() + " " + String.format("%02d/%d", month.getMonthValue(), month.getYear()));
        t.setAccountId(account.getId());
        t.setCardId(card.getId());
        t.setStatementMonth(month.atDay(1));
        return transactionService.view(ws, transactions.save(t));
    }
}
