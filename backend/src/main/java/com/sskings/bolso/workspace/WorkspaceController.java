package com.sskings.bolso.workspace;

import com.sskings.bolso.workspace.WorkspaceDtos.AcceptedInvite;
import com.sskings.bolso.workspace.WorkspaceDtos.InviteCreated;
import com.sskings.bolso.workspace.WorkspaceDtos.InviteView;
import com.sskings.bolso.workspace.WorkspaceDtos.MemberView;
import com.sskings.bolso.workspace.WorkspaceDtos.RoleRequest;
import com.sskings.bolso.workspace.WorkspaceDtos.WorkspaceRequest;
import com.sskings.bolso.workspace.WorkspaceDtos.WorkspaceView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService service;

    @PostMapping("/api/workspaces")
    public ResponseEntity<WorkspaceView> create(@Valid @RequestBody WorkspaceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req.name()));
    }

    @GetMapping("/api/workspaces/{id}")
    public WorkspaceView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/api/workspaces/{id}")
    public WorkspaceView rename(@PathVariable UUID id, @Valid @RequestBody WorkspaceRequest req) {
        return service.rename(id, req.name());
    }

    @DeleteMapping("/api/workspaces/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/workspaces/{id}/members")
    public List<MemberView> members(@PathVariable UUID id) {
        return service.members(id);
    }

    @PutMapping("/api/workspaces/{id}/members/{userId}")
    public MemberView changeRole(@PathVariable UUID id, @PathVariable UUID userId, @Valid @RequestBody RoleRequest req) {
        return service.changeRole(id, userId, req.role());
    }

    @DeleteMapping("/api/workspaces/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/workspaces/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable UUID id) {
        service.leave(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/workspaces/{id}/invites")
    public ResponseEntity<InviteCreated> invite(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createInvite(id));
    }

    @GetMapping("/api/workspaces/{id}/invites")
    public List<InviteView> invites(@PathVariable UUID id) {
        return service.pendingInvites(id);
    }

    @DeleteMapping("/api/workspaces/{id}/invites/{inviteId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @PathVariable UUID inviteId) {
        service.revokeInvite(id, inviteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/invites/{code}/accept")
    public AcceptedInvite accept(@PathVariable String code) {
        return service.accept(code);
    }
}
