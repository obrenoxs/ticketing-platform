package dev.obrenoxs.ticketing.auth.internal.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.obrenoxs.ticketing.auth.internal.AuthSecrets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(AuthSecrets secrets) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(secrets)));
    }

    @Bean
    public JwtDecoder jwtDecoder(AuthSecrets secrets) {
        return NimbusJwtDecoder.withSecretKey(key(secrets))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKey key(AuthSecrets secrets) {
        return new SecretKeySpec(secrets.jwtSigningKeyBytes(), "HmacSHA256");
    }
}