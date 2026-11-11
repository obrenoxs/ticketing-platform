package dev.obrenoxs.ticketing.platform.internal;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.PlatformErrorCode;
import dev.obrenoxs.ticketing.platform.api.RateLimited;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static dev.obrenoxs.ticketing.platform.api.PlatformErrorCode.RATE_LIMITED;

@Component
class RateLimitInterceptor implements HandlerInterceptor {

    private final KeyedRateLimiter limiter;
    private final AppProperties.RateLimit config;

    RateLimitInterceptor(KeyedRateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.config = properties.rateLimit();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!config.enabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimited annotation = method.getMethodAnnotation(RateLimited.class);
        if (annotation == null) {
            annotation = method.getBeanType().getAnnotation(RateLimited.class);
        }
        if (annotation == null) {
            return true;
        }

        String identifier = annotation.value().byIp() ? ipOf(request) : identify(request);
        RateLimitDecision decision = limiter.tryAcquire(annotation.value(), identifier);
        if (decision.allowed()) {
            return true;
        }

        long seconds = Math.max(1, (long) Math.ceil(decision.retryAfter().toMillis() / 1000.0));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        throw new BusinessException(RATE_LIMITED, "Too many requests. Try again later.");
    }

    private String identify(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "acc:" + auth.getName();
        }
        return ipOf(request);
    }

    private String ipOf(HttpServletRequest request) {
        return "ip:" + request.getRemoteAddr();
    }
}
