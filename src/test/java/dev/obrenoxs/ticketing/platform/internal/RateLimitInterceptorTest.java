package dev.obrenoxs.ticketing.platform.internal;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import dev.obrenoxs.ticketing.platform.api.RateLimitPolicy;
import dev.obrenoxs.ticketing.platform.api.RateLimited;
import dev.obrenoxs.ticketing.platform.internal.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitInterceptorTest {

    @RestController
    static class DummyController {
        @GetMapping("/limited")
        @RateLimited(RateLimitPolicy.RESERVE)
        String limited() { return "ok"; }

        @GetMapping("/free")
        String free() { return "ok"; }
    }

    private MockMvc mvc(boolean enabled) {
        AppProperties.RateLimit cfg = new AppProperties.RateLimit(enabled, 60, 2, 10, 20, 10, 5, 5);
        AtomicLong now = new AtomicLong();
        KeyedRateLimiter limiter = new KeyedRateLimiter(cfg, now::get);
        AppProperties props = new AppProperties(null, null, null, null, null, cfg);
        return MockMvcBuilders.standaloneSetup(new DummyController())
                .addInterceptors(new RateLimitInterceptor(limiter, props))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn429WithRetryAfterWhenLimitExceeded() throws Exception {
        MockMvc mvc = mvc(true);
        mvc.perform(get("/limited")).andExpect(status().isOk());
        mvc.perform(get("/limited")).andExpect(status().isOk());
        mvc.perform(get("/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void shouldIgnoreEndpointsWithoutAnnotation() throws Exception {
        MockMvc mvc = mvc(true);
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/free")).andExpect(status().isOk());
        }
    }

    @Test
    void shouldNotLimitWhenDisabled() throws Exception {
        MockMvc mvc = mvc(false);
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/limited")).andExpect(status().isOk());
        }
    }
}
