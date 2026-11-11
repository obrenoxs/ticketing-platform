package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.AuthErrorCode;
import dev.obrenoxs.ticketing.auth.internal.EmailVerificationService;
import dev.obrenoxs.ticketing.auth.internal.SignupService;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.internal.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final SignupService service = mock(SignupService.class);
    private final EmailVerificationService verificationService = mock(EmailVerificationService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(service, verificationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static String body(String name, String email, String password, String cpf) {
        return """
                {"name":"%s","email":"%s","password":"%s","cpf":"%s"}
                """.formatted(name, email, password, cpf);
    }

    @Test
    void shouldReturn202WithGenericMessage() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "ana@example.com", "password-123", "529.982.247-25")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
        verify(service).signup("Ana", "ana@example.com", "password-123", "529.982.247-25");
    }

    @Test
    void shouldReturn400ForInvalidCpf() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "ana@example.com", "password-123", "12345678900")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn400ForShortPasswordAndBadEmail() throws Exception {
        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana", "not-an-email", "short", "52998224725")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn200WhenTokenIsValid() throws Exception {
        mvc.perform(get("/auth/verify-email").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        verify(verificationService).verify("abc");
    }

    @Test
    void shouldReturn400WhenTokenIsInvalid() throws Exception {
        doThrow(new BusinessException(AuthErrorCode.INVALID_VERIFICATION_TOKEN,
                "Invalid or expired verification link."))
                .when(verificationService).verify("bad");

        mvc.perform(get("/auth/verify-email").param("token", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void shouldReturn400WhenTokenParameterIsMissing() throws Exception {
        mvc.perform(get("/auth/verify-email"))
                .andExpect(status().isBadRequest());
    }
}
