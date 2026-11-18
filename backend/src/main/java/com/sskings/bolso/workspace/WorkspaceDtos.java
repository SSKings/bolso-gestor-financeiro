package com.sskings.bolso.workspace;

import com.sskings.bolso.common.PlainText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record WorkspaceRequest(@NotBlank @Size(max = 60) @PlainText String name) {}

    public record WorkspaceView(UUID id, String name, Role role) {}

    public record MemberView(UUID userId, String name, String email, Role role, Instant joinedAt) {}

    public record RoleRequest(@NotNull Role role) {}

    /** O código só aparece aqui, uma vez: depois, só o hash existe. */
    public record InviteCreated(UUID id, String code, Instant expiresAt) {}

    public record InviteView(UUID id, Instant createdAt, Instant expiresAt) {}

    public record AcceptedInvite(UUID workspaceId, String name) {}
}
