package com.sskings.bolso.budget;

import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryKind;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.workspace.WorkspaceAccess;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orçamento por categoria: limite mensal; o gasto do mês soma as despesas da conta e as parcelas do cartão que caem
 * na fatura do mês (a mesma regra do painel). Atenção a partir de 80%; estourado acima de 100%.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    static final int WARNING_PERCENT = 80;

    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final NamedParameterJdbcTemplate jdbc;
    private final WorkspaceAccess access;
    private final com.sskings.bolso.common.Today today;
    private final Clock clock;

    public record BudgetRequest(@NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount) {}

    public enum Status { OK, WARNING, EXCEEDED }

    public record BudgetView(CategoryRef category, BigDecimal amount, BigDecimal spent, BigDecimal remaining, int percent, Status status) {}

    @Transactional(readOnly = true)
    public List<BudgetView> list(UUID ws, YearMonth month) {
        access.member(ws);
        Map<UUID, Category> cats = categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Map<UUID, BigDecimal> spent = spentByCategory(ws, month);
        return budgets.findByWorkspaceId(ws).stream().map(b -> {
            Category c = cats.get(b.getCategoryId());
            BigDecimal s = spent.getOrDefault(b.getCategoryId(), BigDecimal.ZERO);
            int percent = s.multiply(BigDecimal.valueOf(100)).divide(b.getAmount(), 0, RoundingMode.FLOOR).intValue();
            Status status = s.compareTo(b.getAmount()) > 0 ? Status.EXCEEDED : percent >= WARNING_PERCENT ? Status.WARNING : Status.OK;
            return new BudgetView(new CategoryRef(c.getId(), c.getName(), c.getColor()), b.getAmount(), s, b.getAmount().subtract(s), percent, status);
        }).sorted(Comparator.comparingInt(BudgetView::percent).reversed().thenComparing(v -> v.category().name())).toList();
    }

    @Transactional
    public BudgetView set(UUID ws, UUID categoryId, BigDecimal amount) {
        access.member(ws);
        Category c = categories.findByIdAndWorkspaceId(categoryId, ws).orElseThrow(() -> ApiException.notFound("Category not found"));
        if (c.getKind() != CategoryKind.EXPENSE) throw ApiException.badRequest("Budgets are for expense categories");
        Budget b = budgets.findByWorkspaceIdAndCategoryId(ws, categoryId).orElseGet(() -> new Budget(ws, categoryId, clock.instant()));
        b.setAmount(amount);
        b.setUpdatedAt(clock.instant());
        budgets.saveAndFlush(b);
        return list(ws, today.month()).stream().filter(v -> v.category().id().equals(categoryId)).findFirst().orElseThrow();
    }

    @Transactional
    public void delete(UUID ws, UUID categoryId) {
        access.member(ws);
        budgets.delete(budgets.findByWorkspaceIdAndCategoryId(ws, categoryId).orElseThrow(() -> ApiException.notFound("Budget not found")));
    }

    private Map<UUID, BigDecimal> spentByCategory(UUID ws, YearMonth m) {
        var params = new MapSqlParameterSource("ws", ws)
                .addValue("from", Date.valueOf(m.atDay(1))).addValue("to", Date.valueOf(m.atEndOfMonth()))
                .addValue("statement", Date.valueOf(m.atDay(1)));
        Map<UUID, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                SELECT category_id, SUM(amount) AS spent FROM (
                    SELECT category_id, amount FROM transactions
                     WHERE workspace_id = :ws AND type = 'EXPENSE' AND occurred_on BETWEEN :from AND :to
                    UNION ALL
                    SELECT p.category_id, i.amount FROM card_installments i
                      JOIN card_purchases p ON p.id = i.purchase_id AND p.workspace_id = i.workspace_id
                     WHERE i.workspace_id = :ws AND i.statement_month = :statement) x
                 GROUP BY category_id
                """, params, rs -> { out.put(rs.getObject("category_id", UUID.class), rs.getBigDecimal("spent")); });
        return out;
    }
}
