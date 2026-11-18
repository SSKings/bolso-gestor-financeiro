package com.sskings.bolso.account;

import com.sskings.bolso.account.AccountDtos.AccountRequest;
import com.sskings.bolso.account.AccountDtos.AccountView;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.common.Today;
import com.sskings.bolso.transaction.TransactionRepository;
import com.sskings.bolso.workspace.WorkspaceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    static final String NOT_FOUND = "Account not found";

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final com.sskings.bolso.recurrence.RecurrenceRepository recurrences;
    private final BalanceQuery balanceQuery;
    private final WorkspaceAccess access;
    private final Today today;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AccountView> list(UUID ws) {
        access.member(ws);
        Map<UUID, BigDecimal> balances = balanceQuery.balances(ws, today.date());
        return accounts.findByWorkspaceIdOrderByName(ws).stream()
                .map(a -> AccountView.of(a, balances.getOrDefault(a.getId(), a.getInitialBalance()))).toList();
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID ws, UUID id) {
        access.member(ws);
        Account a = find(ws, id);
        return AccountView.of(a, balanceQuery.balances(ws, today.date()).get(id));
    }

    @Transactional
    public AccountView create(UUID ws, AccountRequest req) {
        access.member(ws);
        String name = Texts.clean(req.name());
        if (accounts.existsByWorkspaceIdAndNameIgnoreCase(ws, name)) throw ApiException.conflict("Account name already in use");
        Account a = accounts.saveAndFlush(new Account(ws, name, req.type(), req.initialBalance(), clock.instant()));   // o saldo vem por SQL
        return AccountView.of(a, balanceQuery.balances(ws, today.date()).get(a.getId()));
    }

    @Transactional
    public AccountView update(UUID ws, UUID id, AccountRequest req) {
        access.member(ws);
        Account a = find(ws, id);
        String name = Texts.clean(req.name());
        if (!a.getName().equalsIgnoreCase(name) && accounts.existsByWorkspaceIdAndNameIgnoreCase(ws, name)) {
            throw ApiException.conflict("Account name already in use");
        }
        a.setName(name);
        a.setType(req.type());
        a.setInitialBalance(req.initialBalance());
        if (req.archived() != null) a.setArchived(req.archived());
        accounts.flush();
        return AccountView.of(a, balanceQuery.balances(ws, today.date()).get(id));
    }

    @Transactional
    public void delete(UUID ws, UUID id) {
        access.member(ws);
        Account a = find(ws, id);
        if (transactions.existsByAccount(ws, id) || recurrences.existsByWorkspaceIdAndAccountId(ws, id)) throw ApiException.conflict("Account has transactions; archive it instead");
        accounts.delete(a);
    }

    private Account find(UUID ws, UUID id) {
        return accounts.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
