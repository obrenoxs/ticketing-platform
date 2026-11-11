package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.AuthenticatedAccount;
import dev.obrenoxs.ticketing.auth.internal.EmailVerificationService;
import dev.obrenoxs.ticketing.auth.internal.LoginService;
import dev.obrenoxs.ticketing.auth.internal.SignupService;
import dev.obrenoxs.ticketing.platform.api.RateLimitPolicy;
import dev.obrenoxs.ticketing.platform.api.RateLimited;
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
    private final LoginService loginService;

    public AuthController(SignupService signupService, EmailVerificationService verificationService, LoginService loginService) {
        this.signupService = signupService;
        this.verificationService = verificationService;
        this.loginService = loginService;
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

    @PostMapping("/login")
    @RateLimited(RateLimitPolicy.LOGIN)
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedAccount account = loginService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(account.accountId(), account.role()));
    }
}
