package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.*;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.platform.api.RateLimitPolicy;
import dev.obrenoxs.ticketing.platform.api.RateLimited;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
class AuthController {

    private static final MessageResponse SIGNUP_RESPONSE = new MessageResponse(
            "If the data is valid, you will receive an email to confirm your account.");

    private final SignupService signupService;
    private final EmailVerificationService verificationService;
    private final LoginService loginService;
    private final AccessTokenService accessTokenService;

    public AuthController(SignupService signupService, EmailVerificationService verificationService, LoginService loginService, AccessTokenService accessTokenService) {
        this.signupService = signupService;
        this.verificationService = verificationService;
        this.loginService = loginService;
        this.accessTokenService = accessTokenService;
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
        return ResponseEntity.ok(LoginResponse.bearer(accessTokenService.issue(account)));
    }

    @GetMapping("/me")
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new MeResponse(
                UUID.fromString(jwt.getSubject()),
                Role.valueOf(jwt.getClaimAsString(AccessTokenService.ROLE_CLAIM))));
    }
}
