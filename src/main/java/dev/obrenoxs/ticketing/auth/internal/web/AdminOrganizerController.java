package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.OrganizerApprovalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/organizers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrganizerController {

    private final OrganizerApprovalService approvals;

    AdminOrganizerController(OrganizerApprovalService approvals) {
        this.approvals = approvals;
    }

    @GetMapping("/pending")
    public List<PendingOrganizerResponse> pending(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return approvals.listPending(page, size).stream()
                .map(PendingOrganizerResponse::from)
                .toList();
    }

    @PostMapping("/{accountId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt admin) {
        approvals.approve(accountId, UUID.fromString(admin.getSubject()));
    }

    @PostMapping("/{accountId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID accountId,
                       @Valid @RequestBody RejectOrganizerRequest request,
                       @AuthenticationPrincipal Jwt admin) {
        approvals.reject(accountId, UUID.fromString(admin.getSubject()), request.reason());
    }
}
