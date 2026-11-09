package dev.obrenoxs.ticketing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}