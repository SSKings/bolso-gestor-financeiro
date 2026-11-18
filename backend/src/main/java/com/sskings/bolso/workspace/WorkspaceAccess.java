package com.sskings.bolso.workspace;

import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guarda de acesso: TODA rota de espaço passa por aqui, com o usuário do token.
 * <ul>
 *   <li>Não é membro → 404 "Workspace not found" (não revela se o espaço existe).</li>
 *   <li>É membro, mas a ação é de dono → 403.</li>
 * </ul>
 * A participação é lida do banco a cada requisição: quem é removido perde o acesso na hora, sem esperar o token
 * expirar.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccess {

    static final String NOT_FOUND = "Workspace not found";

    private final MembershipRepository memberships;

    public Membership member(UUID workspaceId) {
        return memberships.findByWorkspaceIdAndUserId(workspaceId, CurrentUser.get().getId())
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    public Membership owner(UUID workspaceId) {
        Membership m = member(workspaceId);
        if (!m.isOwner()) throw ApiException.forbidden("Only owners can do this");
        return m;
    }
}
