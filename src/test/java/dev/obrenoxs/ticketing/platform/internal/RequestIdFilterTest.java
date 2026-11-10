package dev.obrenoxs.ticketing.platform.internal;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.UUID;

class RequestIdFilterTest {

    private static final UUID FIXED = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @RestController
    static class EchoController {
        @GetMapping("/echo")
        String echo() {
            return MDC.get(RequestIdFilter.MDC_KEY);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .addFilters(new RequestIdFilter(() -> FIXED))
            .build();

    @Test
    void shouldGenerateIdWhenHeaderIsMissing() throws Exception {
        mvc.perform(get("/echo"))
                .andExpect(header().string("X-Request-Id", FIXED.toString()))
                .andExpect(content().string(FIXED.toString()));
    }

    @Test
    void shouldReuseValidIncomingId() throws Exception {
        mvc.perform(get("/echo").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(content().string("abc-123"));
    }

    @Test
    void shouldReplaceInvalidIncomingId() throws Exception {
        mvc.perform(get("/echo").header("X-Request-Id", "bad id with spaces"))
                .andExpect(header().string("X-Request-Id", FIXED.toString()));
    }

    @Test
    void shouldClearMdcAfterRequest() throws Exception {
        mvc.perform(get("/echo"));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
