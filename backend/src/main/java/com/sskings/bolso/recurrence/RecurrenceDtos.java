package com.sskings.bolso.recurrence;

import com.sskings.bolso.common.PlainText;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.transaction.TransactionDtos.Ref;
import com.sskings.bolso.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

public final class RecurrenceDtos {

    private RecurrenceDtos() {}

    public record RecurrenceRequest(
            @NotNull TransactionType type,
            @NotBlank @Size(max = 140) @PlainText String description,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount,
            @NotNull @Min(1) @Max(28) Integer dayOfMonth,
            @NotNull UUID categoryId,
            @NotNull UUID accountId,
            @NotNull YearMonth startMonth,
            YearMonth endMonth,
            Boolean active) {}

    public record RecurrenceView(UUID id, TransactionType type, String description, BigDecimal amount, int dayOfMonth,
                                 CategoryRef category, Ref account, YearMonth startMonth, YearMonth endMonth, boolean active) {}

    public enum OccurrenceStatus { PAID, OVERDUE, DUE_SOON, PENDING }

    public record Occurrence(UUID recurrenceId, TransactionType type, String description, YearMonth month, LocalDate dueDate,
                             BigDecimal expectedAmount, OccurrenceStatus status, UUID transactionId, BigDecimal paidAmount,
                             CategoryRef category, Ref account) {}

    public record ConfirmRequest(
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount,
            @NotNull LocalDate date,
            @NotNull UUID accountId) {}
}
