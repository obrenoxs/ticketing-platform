package dev.obrenoxs.ticketing.platform.api;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {
    RateLimitPolicy value();
}
