package com.sskings.bolso.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras de fatura (como os bancos brasileiros fazem). Dias de fechamento e vencimento vão de 1 a 28: todo mês tem
 * esses dias, sem "dia 31 em fevereiro".
 * <ul>
 *   <li>A fatura do mês M fecha no dia de fechamento de M. Compra até esse dia (inclusive) entra em M; depois, em M+1.</li>
 *   <li>Vencimento depois do fechamento → no mesmo mês; no mesmo dia ou antes → no mês seguinte.</li>
 *   <li>A parcela k cai na fatura M + (k − 1).</li>
 * </ul>
 */
public final class Billing {

    private Billing() {}

    public static YearMonth statementFor(LocalDate purchase, int closingDay) {
        YearMonth m = YearMonth.from(purchase);
        return purchase.getDayOfMonth() <= closingDay ? m : m.plusMonths(1);
    }

    public static LocalDate closingDate(YearMonth statement, int closingDay) {
        return statement.atDay(closingDay);
    }

    public static LocalDate dueDate(YearMonth statement, int closingDay, int dueDay) {
        return dueDay > closingDay ? statement.atDay(dueDay) : statement.plusMonths(1).atDay(dueDay);
    }

    /** Divide em centavos: todas iguais, e o resto na primeira (100,00 em 3× = 33,34 + 33,33 + 33,33). */
    public static List<BigDecimal> split(BigDecimal total, int installments) {
        long cents = total.movePointRight(2).longValueExact();
        if (installments < 1 || cents < installments) {
            throw new IllegalArgumentException("Each installment must be at least R$ 0,01");
        }
        long base = cents / installments;
        long first = base + cents % installments;
        List<BigDecimal> parts = new ArrayList<>(installments);
        for (int i = 0; i < installments; i++) parts.add(BigDecimal.valueOf(i == 0 ? first : base, 2));
        return parts;
    }
}
