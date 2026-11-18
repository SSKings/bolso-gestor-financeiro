package com.sskings.bolso.account;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Saldo das contas = saldo inicial + receitas − despesas − pagamentos de fatura − transferências que saem
 * + transferências que entram,
 * até a data informada (lançamento futuro é previsão, não saldo). Sempre filtrado pelo espaço.
 */
@Component
@RequiredArgsConstructor
public class BalanceQuery {

    private static final String SQL = """
            SELECT a.id, a.initial_balance + COALESCE(SUM(
                     CASE WHEN t.type = 'INCOME' THEN t.amount
                          WHEN t.type IN ('EXPENSE', 'CARD_PAYMENT') THEN -t.amount
                          WHEN t.type = 'TRANSFER' AND t.account_id = a.id THEN -t.amount
                          WHEN t.type = 'TRANSFER' AND t.to_account_id = a.id THEN t.amount
                     END), 0) AS balance
              FROM accounts a
              LEFT JOIN transactions t
                ON t.workspace_id = a.workspace_id
               AND (t.account_id = a.id OR t.to_account_id = a.id)
               AND t.occurred_on <= :until
             WHERE a.workspace_id = :ws
             GROUP BY a.id, a.initial_balance
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Map<UUID, BigDecimal> balances(UUID workspaceId, LocalDate until) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        jdbc.query(SQL, new MapSqlParameterSource("ws", workspaceId).addValue("until", Date.valueOf(until)),
                rs -> { out.put(rs.getObject("id", UUID.class), rs.getBigDecimal("balance")); });
        return out;
    }
}
