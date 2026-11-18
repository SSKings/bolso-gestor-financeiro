package com.sskings.bolso.workspace;

import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.common.Tokens;
import com.sskings.bolso.config.BolsoProperties;
import com.sskings.bolso.security.CurrentUser;
import com.sskings.bolso.user.User;
import com.sskings.bolso.user.UserRepository;
import com.sskings.bolso.workspace.WorkspaceDtos.AcceptedInvite;
import com.sskings.bolso.workspace.WorkspaceDtos.InviteCreated;
import com.sskings.bolso.workspace.WorkspaceDtos.InviteView;
import com.sskings.bolso.workspace.WorkspaceDtos.MemberView;
import com.sskings.bolso.workspace.WorkspaceDtos.WorkspaceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Espaços compartilhados: criação, membros e convites.
 * <ul>
 *   <li>Sempre há ao menos um dono: o último não sai, não se remove e não se rebaixa (os membros do espaço ficam
 *       travados durante a checagem — duas saídas simultâneas não passam juntas).</li>
 *   <li>Convite: código aleatório de 256 bits, só o hash no banco, vale uma vez (UPDATE condicional) e por 72 h.
 *       Código inválido, usado, revogado ou vencido → a mesma resposta.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    static final String INVALID_INVITE = "Invalid or expired invite";
    static final String LAST_OWNER = "The last owner cannot leave the workspace";

    private final WorkspaceRepository workspaces;
    private final MembershipRepository memberships;
    private final InviteRepository invites;
    private final UserRepository users;
    private final WorkspaceAccess access;
    private final WorkspaceFactory factory;
    private final BolsoProperties props;
    private final Clock clock;

    @Transactional
    public WorkspaceView create(String name) {
        Workspace ws = factory.create(Texts.clean(name), CurrentUser.get());
        return new WorkspaceView(ws.getId(), ws.getName(), Role.OWNER);
    }

    @Transactional(readOnly = true)
    public WorkspaceView get(UUID id) {
        Membership m = access.member(id);
        return new WorkspaceView(id, workspaces.getReferenceById(id).getName(), m.getRole());
    }

    @Transactional
    public WorkspaceView rename(UUID id, String name) {
        Membership m = access.owner(id);
        Workspace ws = workspaces.findById(id).orElseThrow();
        ws.setName(Texts.clean(name));
        return new WorkspaceView(id, ws.getName(), m.getRole());
    }

    @Transactional
    public void delete(UUID id) {
        access.owner(id);
        workspaces.deleteById(id);   // cascata no banco: membros, convites, contas, categorias e lançamentos
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID id) {
        access.member(id);
        List<Membership> list = memberships.findByWorkspaceIdOrderByJoinedAt(id);
        Map<UUID, User> byId = users.findAllById(list.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(m -> {
            User u = byId.get(m.getUserId());
            return new MemberView(u.getId(), u.getName(), u.getEmail(), m.getRole(), m.getJoinedAt());
        }).toList();
    }

    @Transactional
    public MemberView changeRole(UUID id, UUID userId, Role role) {
        access.owner(id);
        List<Membership> all = memberships.lockAll(id);
        Membership target = find(all, userId);
        if (target.isOwner() && role != Role.OWNER && owners(all) == 1) throw ApiException.badRequest(LAST_OWNER);
        target.setRole(role);
        User u = users.findById(userId).orElseThrow();
        return new MemberView(u.getId(), u.getName(), u.getEmail(), role, target.getJoinedAt());
    }

    @Transactional
    public void removeMember(UUID id, UUID userId) {
        access.owner(id);
        removeLocked(id, userId);
    }

    @Transactional
    public void leave(UUID id) {
        access.member(id);
        removeLocked(id, CurrentUser.get().getId());
    }

    private void removeLocked(UUID id, UUID userId) {
        List<Membership> all = memberships.lockAll(id);
        Membership target = find(all, userId);
        if (target.isOwner() && owners(all) == 1) throw ApiException.badRequest(LAST_OWNER);
        memberships.delete(target);
    }

    private static Membership find(List<Membership> all, UUID userId) {
        return all.stream().filter(m -> m.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Member not found"));
    }

    private static long owners(List<Membership> all) {
        return all.stream().filter(Membership::isOwner).count();
    }

    @Transactional
    public InviteCreated createInvite(UUID id) {
        access.owner(id);
        String code = Tokens.random();
        Instant now = clock.instant();
        Invite invite = invites.save(new Invite(id, Tokens.sha256(code), CurrentUser.get().getId(), now,
                now.plus(Duration.ofHours(props.invite().ttlHours()))));
        return new InviteCreated(invite.getId(), code, invite.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<InviteView> pendingInvites(UUID id) {
        access.owner(id);
        return invites.findPending(id, clock.instant()).stream()
                .map(i -> new InviteView(i.getId(), i.getCreatedAt(), i.getExpiresAt())).toList();
    }

    @Transactional
    public void revokeInvite(UUID id, UUID inviteId) {
        access.owner(id);
        Invite invite = invites.findByIdAndWorkspaceId(inviteId, id).orElseThrow(() -> ApiException.notFound("Invite not found"));
        invites.delete(invite);
    }

    @Transactional
    public AcceptedInvite accept(String code) {
        if (!Tokens.wellFormed(code)) throw ApiException.notFound(INVALID_INVITE);
        Invite invite = invites.findByTokenHash(Tokens.sha256(code)).orElseThrow(() -> ApiException.notFound(INVALID_INVITE));
        UUID userId = CurrentUser.get().getId();
        Instant now = clock.instant();
        if (invite.getUsedAt() != null || !invite.getExpiresAt().isAfter(now)) throw ApiException.notFound(INVALID_INVITE);
        if (memberships.findByWorkspaceIdAndUserId(invite.getWorkspaceId(), userId).isPresent()) {
            throw ApiException.conflict("Already a member");
        }
        if (invites.markUsed(invite.getId(), userId, now) == 0) throw ApiException.notFound(INVALID_INVITE);
        memberships.save(new Membership(invite.getWorkspaceId(), userId, Role.MEMBER, now));
        return new AcceptedInvite(invite.getWorkspaceId(), workspaces.getReferenceById(invite.getWorkspaceId()).getName());
    }
}
