package com.sskings.bolso.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sskings.bolso.common.PlainText;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {}

    /** Valor de R$ 0,01 a R$ 999.999.999,99, com até 2 casas. Data entre 2000 e um ano à frente (o serviço confere). */
    public record TransactionRequest(
            @NotNull TransactionType type,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount,
            @NotNull @JsonProperty("date") LocalDate date,
            @NotBlank @Size(max = 140) @PlainText String description,
            @NotNull UUID accountId,
            UUID toAccountId,
            UUID categoryId) {}

    public record Ref(UUID id, String name) {}

    public record CategoryRef(UUID id, String name, String color) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionView(UUID id, TransactionType type, BigDecimal amount, LocalDate date, String description,
                                  Ref account, Ref toAccount, CategoryRef category, Ref card, java.time.YearMonth statement,
                                  Ref createdBy, Instant createdAt) {}

    public record Page(List<TransactionView> items, long total, int page, int size) {}
}
