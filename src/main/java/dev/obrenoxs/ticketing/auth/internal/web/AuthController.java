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
    private final RefreshTokenService refreshTokenService;

    public AuthController(SignupService signupService, EmailVerificationService verificationService, LoginService loginService, AccessTokenService accessTokenService, RefreshTokenService refreshTokenService) {
        this.signupService = signupService;
        this.verificationService = verificationService;
        this.loginService = loginService;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
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
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedAccount account = loginService.login(request.email(), request.password());
        return ResponseEntity.ok(TokenResponse.of(
                accessTokenService.issue(account),
                refreshTokenService.issueForLogin(account.accountId())));
    }

    @GetMapping("/me")
    ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new MeResponse(
                UUID.fromString(jwt.getSubject()),
                Role.valueOf(jwt.getClaimAsString(AccessTokenService.ROLE_CLAIM))));
    }

    @PostMapping("/refresh")
    @RateLimited(RateLimitPolicy.LOGIN)
    ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RotationResult result = refreshTokenService.rotate(request.refreshToken());
        return ResponseEntity.ok(TokenResponse.of(
                accessTokenService.issue(result.account()), result.refreshToken()));
    }

    @PostMapping("/logout")
    @RateLimited(RateLimitPolicy.LOGIN)
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
