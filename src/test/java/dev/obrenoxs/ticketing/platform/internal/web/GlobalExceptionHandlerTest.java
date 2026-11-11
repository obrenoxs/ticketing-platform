package dev.obrenoxs.ticketing.platform.internal.web;

import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.ErrorCode;
import dev.obrenoxs.ticketing.platform.api.PlatformErrorCode;
import dev.obrenoxs.ticketing.platform.api.TooManyRequestsException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    record CreateRequest(@NotBlank String name) {
    }

    enum TestErrorCode implements ErrorCode {
        SOMETHING_WRONG(422);

        private final int status;

        TestErrorCode(int status) {
            this.status = status;
        }

        @Override
        public int status() {
            return status;
        }
    }

    @RestController
    static class FakeController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(TestErrorCode.SOMETHING_WRONG, "Business rule broken.");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new RuntimeException("secret detail");
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody CreateRequest request) {
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionReturnsItsStatusAndCode() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOMETHING_WRONG"))
                .andExpect(jsonPath("$.detail").value("Business rule broken."));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @RestController
    static class ThrottledController {
        @GetMapping("/throttled")
        String throttled() {
            throw new TooManyRequestsException(PlatformErrorCode.RATE_LIMITED, "Slow down.", Duration.ofMillis(2500));
        }
    }

    @Test
    void shouldReturn429WithRoundedUpRetryAfter() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrottledController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/throttled"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}