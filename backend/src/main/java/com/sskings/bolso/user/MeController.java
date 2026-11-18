package com.sskings.bolso.user;

import com.sskings.bolso.security.CurrentUser;
import com.sskings.bolso.workspace.MembershipRepository;
import com.sskings.bolso.workspace.Role;
import com.sskings.bolso.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final MembershipRepository memberships;
    private final WorkspaceRepository workspaces;

    public record WorkspaceSummary(UUID id, String name, Role role) {}

    public record MeResponse(UUID id, String name, String email, List<WorkspaceSummary> workspaces) {}

    @GetMapping("/api/me")
    @Transactional(readOnly = true)
    public MeResponse me() {
        User u = CurrentUser.get();
        List<WorkspaceSummary> list = memberships.findByUserIdOrderByJoinedAt(u.getId()).stream()
                .map(m -> new WorkspaceSummary(m.getWorkspaceId(), workspaces.getReferenceById(m.getWorkspaceId()).getName(), m.getRole()))
                .toList();
        return new MeResponse(u.getId(), u.getName(), u.getEmail(), list);
    }
}
