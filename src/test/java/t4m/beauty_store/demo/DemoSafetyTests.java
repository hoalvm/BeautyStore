package t4m.beauty_store.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import t4m.beauty_store.config.VNPayConfig;

import static org.assertj.core.api.Assertions.*;

class DemoSafetyTests {
    @Test
    void demoRejectsRemoteBindingProductionMixAndNonH2Database() throws Exception {
        var configuration = new DemoSafetyConfiguration();
        var environment = local();
        configuration.validateDemoIsolation(environment).afterPropertiesSet();
        environment.setActiveProfiles("demo", "prod");
        assertThatThrownBy(() -> configuration.validateDemoIsolation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
        environment.setActiveProfiles("demo");
        environment.setProperty("server.address", "0.0.0.0");
        assertThatThrownBy(() -> configuration.validateDemoIsolation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
        environment.setProperty("server.address", "127.0.0.1");
        environment.setProperty("spring.datasource.url", "jdbc:mysql://localhost/beauty_store");
        assertThatThrownBy(() -> configuration.validateDemoIsolation(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void demoCannotEnablePaymentEvenIfCredentialsAreAccidentallyProvided() {
        var config = new VNPayConfig();
        config.setEnvironment(local());
        ReflectionTestUtils.setField(config, "tmnCode", "accidental-merchant");
        ReflectionTestUtils.setField(config, "hashSecret", "not-a-real-merchant-secret");
        assertThat(config.isConfigured()).isFalse();
        assertThatThrownBy(config::requireConfigured).isInstanceOf(IllegalStateException.class);
    }

    private MockEnvironment local() {
        var environment = new MockEnvironment()
                .withProperty("server.address", "127.0.0.1")
                .withProperty("spring.datasource.url", "jdbc:h2:mem:demo");
        environment.setActiveProfiles("demo");
        return environment;
    }
}
