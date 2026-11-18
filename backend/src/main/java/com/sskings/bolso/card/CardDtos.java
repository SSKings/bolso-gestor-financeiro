package com.sskings.bolso.card;

import com.sskings.bolso.common.PlainText;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.transaction.TransactionDtos.Ref;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class CardDtos {

    private CardDtos() {}

    /** Sem campo de número: só os 4 últimos dígitos (opcional) — um "number" no JSON é recusado. */
    public record CardRequest(
            @NotBlank @Size(max = 60) @PlainText String name,
            @Pattern(regexp = "^[0-9]{4}$", message = "must be exactly the last 4 digits") String last4,
            @NotNull @Min(1) @Max(28) Integer closingDay,
            @NotNull @Min(1) @Max(28) Integer dueDay,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0") @DecimalMax("999999999.99") BigDecimal creditLimit,
            Boolean archived) {}

    public record CardView(UUID id, String name, @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String last4, int closingDay, int dueDay, BigDecimal creditLimit,
                           BigDecimal available, boolean archived) {}

    public record PurchaseRequest(
            @NotBlank @Size(max = 140) @PlainText String description,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount,
            @NotNull @Min(1) @Max(48) Integer installments,
            @NotNull LocalDate date,
            @NotNull UUID categoryId) {}

    public record Part(int number, BigDecimal amount, YearMonth statement) {}

    public record PurchaseView(UUID id, String description, BigDecimal amount, int installments, LocalDate date,
                               CategoryRef category, List<Part> parts) {}

    public enum StatementStatus { OPEN, CLOSED, PAID }

    public record StatementItem(UUID purchaseId, String description, int number, int of, BigDecimal amount,
                                LocalDate purchaseDate, CategoryRef category) {}

    public record StatementPayment(UUID transactionId, LocalDate date, BigDecimal amount, Ref account) {}

    public record StatementView(YearMonth month, LocalDate closingDate, LocalDate dueDate, StatementStatus status,
                                BigDecimal total, BigDecimal paid, BigDecimal remaining,
                                List<StatementItem> items, List<StatementPayment> payments) {}

    public record PaymentRequest(
            @NotNull UUID accountId,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount,
            @NotNull LocalDate date) {}

    /** Resumo para o painel: a fatura do mês de cada cartão. */
    public record StatementSummary(UUID cardId, String name, BigDecimal statementTotal, LocalDate dueDate, StatementStatus status) {}
}
