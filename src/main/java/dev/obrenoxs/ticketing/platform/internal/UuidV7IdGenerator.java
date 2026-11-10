package dev.obrenoxs.ticketing.platform.internal;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.springframework.stereotype.Component;

@Component
class UuidV7IdGenerator implements IdGenerator {

    @Override
    public UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
