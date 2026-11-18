package com.sskings.bolso.account;

import com.sskings.bolso.common.PlainText;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {}

    /** Saldo inicial pode ser negativo (cheque especial); até 2 casas e ± 999 milhões. */
    public record AccountRequest(
            @NotBlank @Size(max = 60) @PlainText String name,
            @NotNull AccountType type,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("-999999999.99") @DecimalMax("999999999.99") BigDecimal initialBalance,
            Boolean archived) {}

    public record AccountView(UUID id, String name, AccountType type, BigDecimal initialBalance, BigDecimal balance, boolean archived) {
        static AccountView of(Account a, BigDecimal balance) {
            return new AccountView(a.getId(), a.getName(), a.getType(), a.getInitialBalance(), balance, a.isArchived());
        }
    }
}
