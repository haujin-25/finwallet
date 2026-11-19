package finwallet_api.funding.dev;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
@Conditional(DevFundingEnabled.class)
public class DevFundingSecurityConfig {

    static final String SETTLE_PATH = "/api/dev/funding/{reference}/settle";
    static final String TOKEN_HEADER = "X-Dev-Funding-Token";
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    @Bean
    @Order(1)
    SecurityFilterChain devFundingSecurityFilterChain(HttpSecurity http, Environment environment) {
        String token = environment.getProperty("finwallet.dev.funding.token", "");
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);

        return http
                .securityMatcher(SETTLE_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, SETTLE_PATH)
                        .access((authentication, context) -> {
                            var request = context.getRequest();
                            String provided = request.getHeader(TOKEN_HEADER);
                            return new AuthorizationDecision(token.length() >= 32 && !token.isBlank()
                                    && LOOPBACK.contains(request.getRemoteAddr())
                                    && provided != null
                                    && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8)));
                        })
                        .anyRequest().denyAll())
                .build();
    }
}
