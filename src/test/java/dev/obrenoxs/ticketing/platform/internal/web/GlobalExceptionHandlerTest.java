package dev.obrenoxs.ticketing.platform.internal.web;

import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

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
}