package com.sskings.bolso.forecast;

import com.sskings.bolso.account.AccountDtos.AccountView;
import com.sskings.bolso.account.AccountService;
import com.sskings.bolso.card.Billing;
import com.sskings.bolso.card.CardDtos.StatementView;
import com.sskings.bolso.card.CardService;
import com.sskings.bolso.card.CreditCard;
import com.sskings.bolso.card.CreditCardRepository;
import com.sskings.bolso.common.Today;
import com.sskings.bolso.recurrence.RecurrenceDtos.Occurrence;
import com.sskings.bolso.recurrence.RecurrenceDtos.OccurrenceStatus;
import com.sskings.bolso.recurrence.RecurrenceService;
import com.sskings.bolso.transaction.TransactionType;
import com.sskings.bolso.workspace.WorkspaceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Previsão do mês atual: quanto vai sobrar no fim do mês.
 * <pre>
 * previsto = saldo de hoje
 *          + receitas que ainda vão entrar (contas fixas de receita não confirmadas, receitas agendadas)
 *          − contas fixas não pagas (inclusive as atrasadas: o dinheiro ainda vai sair)
 *          − lançamentos agendados para depois de hoje no mês (despesas e pagamentos de fatura)
 *          − o que falta pagar das faturas que vencem no mês
 * </pre>
 * Sem contagem dupla: conta fixa confirmada com data futura aparece só como agendada (a ocorrência fica "paga");
 * pagamento de fatura agendado já reduz o que falta da fatura. Transferência não muda o total.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final AccountService accounts;
    private final RecurrenceService recurrences;
    private final CardService cards;
    private final CreditCardRepository cardRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final WorkspaceAccess access;
    private final Today today;

    public enum Kind { SCHEDULED, BILL, CARD_STATEMENT }

    public enum Direction { IN, OUT }

    public record Item(Kind kind, String description, LocalDate date, BigDecimal amount, Direction direction) {}

    public record Forecast(YearMonth month, LocalDate today, BigDecimal currentBalance, BigDecimal pendingIncome,
                           BigDecimal pendingExpense, BigDecimal projectedBalance, List<Item> items) {}

    @Transactional(readOnly = true)
    public Forecast current(UUID ws) {
        access.member(ws);
        LocalDate now = today.date();
        YearMonth month = YearMonth.from(now);
        List<Item> items = new ArrayList<>();

        var params = new MapSqlParameterSource("ws", ws).addValue("after", Date.valueOf(now))
                .addValue("end", Date.valueOf(month.atEndOfMonth()));
        items.addAll(jdbc.query("""
                SELECT description, occurred_on, amount, type FROM transactions
                 WHERE workspace_id = :ws AND occurred_on > :after AND occurred_on <= :end
                   AND type IN ('INCOME', 'EXPENSE', 'CARD_PAYMENT')
                """, params, (rs, i) -> new Item(Kind.SCHEDULED, rs.getString("description"), rs.getDate("occurred_on").toLocalDate(),
                rs.getBigDecimal("amount"), "INCOME".equals(rs.getString("type")) ? Direction.IN : Direction.OUT)));

        for (Occurrence o : recurrences.occurrences(ws, month)) {
            if (o.status() == OccurrenceStatus.PAID) continue;
            items.add(new Item(Kind.BILL, o.description(), o.dueDate(), o.expectedAmount(),
                    o.type() == TransactionType.INCOME ? Direction.IN : Direction.OUT));
        }

        for (CreditCard c : cardRepository.findByWorkspaceIdOrderByName(ws)) {
            for (YearMonth st : List.of(month.minusMonths(1), month)) {
                LocalDate due = Billing.dueDate(st, c.getClosingDay(), c.getDueDay());
                if (!YearMonth.from(due).equals(month)) continue;
                StatementView s = cards.statement(ws, c.getId(), st);
                if (s.remaining().signum() > 0) items.add(new Item(Kind.CARD_STATEMENT, "Fatura " + c.getName(), due, s.remaining(), Direction.OUT));
            }
        }

        items.sort(Comparator.comparing(Item::date).thenComparing(Item::description));
        BigDecimal in = sum(items, Direction.IN);
        BigDecimal out = sum(items, Direction.OUT);
        BigDecimal balance = accounts.list(ws).stream().map(AccountView::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Forecast(month, now, balance, in, out, balance.add(in).subtract(out), items);
    }

    private static BigDecimal sum(List<Item> items, Direction d) {
        return items.stream().filter(i -> i.direction() == d).map(Item::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
