package com.sskings.bolso.telegram;

import com.sskings.bolso.user.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

/**
 * Executa como o usuário dono da conversa: os serviços do app conferem a participação no espaço (WorkspaceAccess)
 * exatamente como numa requisição HTTP. O contexto anterior é restaurado sempre.
 */
final class RunAs {

    private RunAs() {}

    static <T> T user(User user, Supplier<T> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(ctx);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
