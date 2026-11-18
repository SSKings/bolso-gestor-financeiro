package com.sskings.bolso.dashboard;

import com.sskings.bolso.account.AccountDtos.AccountView;
import com.sskings.bolso.account.AccountService;
import com.sskings.bolso.common.Today;
import com.sskings.bolso.workspace.WorkspaceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Painel do mês: entradas, saídas e resultado, gastos por categoria, o saldo de hoje de cada conta e a fatura do mês
 * de cada cartão.
 * <ul>
 *   <li>Transferência não é gasto nem receita (o dinheiro só mudou de conta).</li>
 *   <li>Compra no cartão conta no mês da FATURA em que a parcela cai, na categoria da compra; o pagamento da fatura
 *       não conta (contaria o mesmo gasto duas vezes).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountService accounts;
    private final com.sskings.bolso.card.CardService cards;
    private final WorkspaceAccess access;
    private final Today today;

    public record CategoryAmount(UUID categoryId, String name, String color, BigDecimal amount) {}

    public record Dashboard(YearMonth month, BigDecimal income, BigDecimal expense, BigDecimal net,
                            List<CategoryAmount> expensesByCategory, List<AccountView> accounts, BigDecimal totalBalance,
                            List<com.sskings.bolso.card.CardDtos.StatementSummary> cards) {}

    @Transactional(readOnly = true)
    public Dashboard month(UUID ws, YearMonth month) {
        access.member(ws);
        YearMonth m = month != null ? month : today.month();
        var params = new MapSqlParameterSource("ws", ws)
                .addValue("from", Date.valueOf(m.atDay(1))).addValue("to", Date.valueOf(m.atEndOfMonth()))
                .addValue("statement", Date.valueOf(m.atDay(1)));
        var totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE type = 'INCOME'), 0)  AS income,
                       COALESCE(SUM(amount) FILTER (WHERE type = 'EXPENSE'), 0) AS expense
                  FROM transactions
                 WHERE workspace_id = :ws AND occurred_on BETWEEN :from AND :to
                """, params);
        BigDecimal income = (BigDecimal) totals.get("income");
        BigDecimal card = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM card_installments WHERE workspace_id = :ws AND statement_month = :statement
                """, params, BigDecimal.class);
        BigDecimal expense = ((BigDecimal) totals.get("expense")).add(card);
        List<CategoryAmount> byCategory = jdbc.query("""
                SELECT c.id, c.name, c.color, SUM(x.amount) AS amount
                  FROM (SELECT category_id, amount FROM transactions
                         WHERE workspace_id = :ws AND type = 'EXPENSE' AND occurred_on BETWEEN :from AND :to
                        UNION ALL
                        SELECT p.category_id, i.amount FROM card_installments i
                          JOIN card_purchases p ON p.id = i.purchase_id AND p.workspace_id = i.workspace_id
                         WHERE i.workspace_id = :ws AND i.statement_month = :statement) x
                  JOIN categories c ON c.id = x.category_id AND c.workspace_id = :ws
                 GROUP BY c.id, c.name, c.color
                 ORDER BY amount DESC, c.name
                """, params, (rs, i) -> new CategoryAmount(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("color"), rs.getBigDecimal("amount")));
        List<AccountView> list = accounts.list(ws);
        BigDecimal total = list.stream().map(AccountView::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dashboard(m, income, expense, income.subtract(expense), byCategory, list, total, cards.summaries(ws, m));
    }
}
