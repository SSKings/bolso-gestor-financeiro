package com.sskings.bolso.security;

import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * O usuário da requisição vem SEMPRE do token validado — nunca de um id no corpo ou na URL (no projeto antigo, o
 * "usuario_id" do corpo deixava qualquer um lançar na conta de outro).
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) return user;
        throw ApiException.unauthorized("Authentication required");
    }
}
