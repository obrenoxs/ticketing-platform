package dev.obrenoxs.ticketing.auth.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@EnableConfigurationProperties(AdminBootstrapProperties.class)
class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService service;
    private final AdminBootstrapProperties properties;

    AdminBootstrapRunner(AdminBootstrapService service, AdminBootstrapProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.bootstrap(properties);
    }
}
