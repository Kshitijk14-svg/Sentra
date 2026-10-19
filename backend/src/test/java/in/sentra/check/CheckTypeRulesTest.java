package in.sentra.check;

import in.sentra.probe.CheckType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckTypeRulesTest {

    private final CheckTypeRules rules = new CheckTypeRules();

    private static CreateCheckRequest http(String target, Integer interval) {
        return new CreateCheckRequest(null, "  Site  ", null, target, null, null, interval, null, null, null, null);
    }

    private static CreateCheckRequest tcp(String host, Integer port) {
        return new CreateCheckRequest(CheckType.TCP, "Redis", null, null, null, null, null, null, null, host, port);
    }

    @Test
    void missingTypeMeansHttpWithDefaults() {
        Check c = new Check();
        rules.apply(c, http("https://example.com/health", null));
        assertThat(c.getType()).isEqualTo(CheckType.HTTP);
        assertThat(c.getName()).isEqualTo("Site");
        assertThat(c.getSite()).isEqualTo("example.com");
        assertThat(c.getIntervalSeconds()).isEqualTo(60);
        assertThat(c.getExpectedStatus()).isEqualTo(200);
        assertThat(c.getFailureThreshold()).isEqualTo(3);
        assertThat(c.getRecoveryThreshold()).isEqualTo(2);
    }

    @Test
    void httpRequiresAnHttpUrl() {
        assertThatThrownBy(() -> rules.apply(new Check(), http("ftp://example.com", null)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("http(s) URL");
        assertThatThrownBy(() -> rules.apply(new Check(), http(null, null)))
                .isInstanceOf(InvalidCheckException.class);
    }

    @Test
    void intervalBelowTheMinimumIsRejected() {
        assertThatThrownBy(() -> rules.apply(new Check(), http("https://example.com", 5)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("at least 10");
    }

    @Test
    void tcpStoresHostAndPortInConfigAndBuildsADisplayTarget() {
        Check c = new Check();
        rules.apply(c, tcp("redis.internal.example.com", 6379));
        assertThat(c.getType()).isEqualTo(CheckType.TCP);
        assertThat(c.getConfig()).containsEntry("host", "redis.internal.example.com").containsEntry("port", 6379);
        assertThat(c.getTarget()).isEqualTo("tcp://redis.internal.example.com:6379");
        assertThat(c.getSite()).isEqualTo("redis.internal.example.com");
        assertThat(c.getTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void tcpRejectsBadHostsAndMissingPort() {
        assertThatThrownBy(() -> rules.apply(new Check(), tcp("bad host!", 80)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("host");
        assertThatThrownBy(() -> rules.apply(new Check(), tcp("", 80)))
                .isInstanceOf(InvalidCheckException.class);
        assertThatThrownBy(() -> rules.apply(new Check(), tcp("example.com", null)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("port");
    }

    @Test
    void explicitSiteOverridesTheDerivedOne() {
        Check c = new Check();
        rules.apply(c, new CreateCheckRequest(CheckType.TCP, "Cache", "Shop backend", null, null, null, null,
                null, null, "10.0.0.5", 6379));
        assertThat(c.getSite()).isEqualTo("Shop backend");
    }
}
