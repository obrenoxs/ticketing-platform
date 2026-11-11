package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.SignupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private static final MessageResponse SIGNUP_RESPONSE = new MessageResponse(
            "If the data is valid, you will receive an email to confirm your account.");

    private final SignupService signupService;

    AuthController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping("/signup")
    ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest request) {
        signupService.signup(request.name(), request.email(), request.password(), request.cpf());
        return ResponseEntity.accepted().body(SIGNUP_RESPONSE);
    }
}
