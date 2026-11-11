package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.EmailVerificationService;
import dev.obrenoxs.ticketing.auth.internal.SignupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
class AuthController {

    private static final MessageResponse SIGNUP_RESPONSE = new MessageResponse(
            "If the data is valid, you will receive an email to confirm your account.");

    private final SignupService signupService;
    private final EmailVerificationService verificationService;

    AuthController(SignupService signupService, EmailVerificationService verificationService) {
        this.signupService = signupService;
        this.verificationService = verificationService;
    }

    @GetMapping("/verify-email")
    ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        verificationService.verify(token);
        return ResponseEntity.ok(new MessageResponse("Email confirmed. You can now log in."));
    }

    @PostMapping("/signup")
    ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request.name(), request.email(), request.password(), request.cpf());
        return ResponseEntity.accepted().body(SIGNUP_RESPONSE);
    }
}
