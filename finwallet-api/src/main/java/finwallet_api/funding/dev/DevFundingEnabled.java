package finwallet_api.funding.dev;

import java.util.Arrays;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class DevFundingEnabled implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        String[] profiles = environment.getActiveProfiles();
        // Refuse mixed deployments and require an explicitly active profile.
        return profiles.length > 0
                && Arrays.stream(profiles).allMatch(profile -> "local".equals(profile) || "test".equals(profile))
                && "true".equalsIgnoreCase(environment.getProperty("finwallet.dev.funding.enabled"));
    }
}
