package t4m.beauty_store.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreRuntimeCapabilities {
    private final Environment environment;
    private final VNPayConfig vnPayConfig;

    public boolean isDemo() { return environment.acceptsProfiles(Profiles.of("demo")); }
    public boolean isVnpayAvailable() { return !isDemo() && vnPayConfig.isConfigured(); }
}
