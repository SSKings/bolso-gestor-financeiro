package com.sskings.bolso.workspace;

import com.sskings.bolso.account.Account;
import com.sskings.bolso.account.AccountRepository;
import com.sskings.bolso.account.AccountType;
import com.sskings.bolso.category.CategorySeeder;
import com.sskings.bolso.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/** Cria um espaço pronto para usar: o criador como dono, categorias padrão e uma conta "Carteira". */
@Component
@RequiredArgsConstructor
public class WorkspaceFactory {

    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final CategorySeeder categorySeeder;
    private final Clock clock;

    public Workspace create(String name, User owner) {
        Instant now = clock.instant();
        Workspace ws = workspaces.save(new Workspace(name, now));
        memberships.save(new Membership(ws.getId(), owner.getId(), Role.OWNER, now));
        categorySeeder.seed(ws.getId(), now);
        accounts.save(new Account(ws.getId(), "Carteira", AccountType.CASH, BigDecimal.ZERO, now));
        return ws;
    }
}
