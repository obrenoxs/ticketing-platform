package dev.obrenoxs.ticketing.auth.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectOrganizerRequest(@NotBlank @Size(max = 500) String reason) {
}
