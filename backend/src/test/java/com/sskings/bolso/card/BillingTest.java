package com.sskings.bolso.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de fatura do cartão (como os bancos brasileiros fazem). */
class BillingTest {

    @ParameterizedTest(name = "compra em {0}, fecha dia {1} → fatura de {2}")
    @CsvSource({
            "2026-11-05, 10, 2026-11",   // antes do fechamento: fatura do mês
            "2026-11-10, 10, 2026-11",   // NO dia do fechamento: ainda entra
            "2026-11-11, 10, 2026-12",   // depois: próxima fatura
            "2026-12-20, 10, 2027-01",   // vira o ano
            "2026-02-28, 28, 2026-02",
            "2026-01-31, 28, 2026-02"})
    void statementOfPurchase(LocalDate purchase, int closingDay, YearMonth expected) {
        assertThat(Billing.statementFor(purchase, closingDay)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Fechamento e vencimento: vencimento depois do fechamento no mesmo mês; antes dele, no mês seguinte")
    void closingAndDueDates() {
        YearMonth nov = YearMonth.of(2026, 11);
        assertThat(Billing.closingDate(nov, 10)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(Billing.dueDate(nov, 10, 17)).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(Billing.dueDate(nov, 25, 5)).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(Billing.dueDate(YearMonth.of(2026, 12), 25, 5)).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(Billing.dueDate(nov, 10, 10)).as("mesmo dia: vence no mês seguinte").isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    @DisplayName("Parcelas: soma exata do total; o resto dos centavos vai na primeira")
    void split() {
        assertThat(Billing.split(new BigDecimal("100.00"), 3)).containsExactly(
                new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(Billing.split(new BigDecimal("0.05"), 3)).containsExactly(
                new BigDecimal("0.03"), new BigDecimal("0.01"), new BigDecimal("0.01"));
        assertThat(Billing.split(new BigDecimal("1200.00"), 12)).allSatisfy(p -> assertThat(p).isEqualByComparingTo("100.00"));
        assertThat(Billing.split(new BigDecimal("9.99"), 1)).containsExactly(new BigDecimal("9.99"));
        List<BigDecimal> parts = Billing.split(new BigDecimal("999999999.99"), 48);
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("999999999.99");
    }

    @Test
    @DisplayName("Mais parcelas que centavos é recusado (parcela de R$ 0,00 não existe)")
    void splitTooSmall() {
        assertThatThrownBy(() -> Billing.split(new BigDecimal("0.02"), 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
