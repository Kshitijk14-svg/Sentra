package in.sentra.check;

import in.sentra.probe.CheckType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckTypeRulesTest {

    private final CheckTypeRules rules = new CheckTypeRules();

    private static CreateCheckRequest http(String target, Integer interval) {
        return new CreateCheckRequest(null, "  Site  ", null, target, null, null, interval, null, null, null, null, null, null, null);
    }

    private static CreateCheckRequest tcp(String host, Integer port) {
        return new CreateCheckRequest(CheckType.TCP, "Redis", null, null, null, null, null, null, null, host, port, null, null, null);
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
                null, null, "10.0.0.5", 6379, null, null, null));
        assertThat(c.getSite()).isEqualTo("Shop backend");
    }

    @Test
    void tlsDefaultsToPort443HourlyAndAFourteenDayWarning() {
        Check c = new Check();
        rules.apply(c, new CreateCheckRequest(CheckType.TLS, "Cert", null, null, null, null, null,
                null, null, "example.com", null, null, null, null));
        assertThat(c.getTarget()).isEqualTo("tls://example.com:443");
        assertThat(c.getConfig()).containsEntry("port", 443).containsEntry("warnDays", 14);
        assertThat(c.getIntervalSeconds()).isEqualTo(3600);
        assertThat(c.getFailureThreshold()).isEqualTo(1);
        assertThat(c.getSite()).isEqualTo("example.com");
    }

    @Test
    void tlsRequiresAValidHost() {
        assertThatThrownBy(() -> rules.apply(new Check(), new CreateCheckRequest(CheckType.TLS, "Cert", null, null,
                null, null, null, null, null, "not a host!", null, null, null, null)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("host");
    }

    private static CreateCheckRequest cron(Integer period, Integer grace) {
        return new CreateCheckRequest(CheckType.CRON, "Nightly backup", null, null, null, null, null, null, null,
                null, null, null, period, grace);
    }

    @Test
    void cronGetsATokenGraceDefaultAndItsPeriodAsInterval() {
        Check c = new Check();
        rules.apply(c, cron(86400, null));
        assertThat(c.getPingToken()).hasSize(32);
        assertThat(c.getConfig()).containsEntry("periodSeconds", 86400).containsEntry("graceSeconds", 3600);
        assertThat(c.getIntervalSeconds()).isEqualTo(86400);
        assertThat(c.getFailureThreshold()).isEqualTo(1);
        assertThat(c.getSite()).isEqualTo("Scheduled jobs");
    }

    @Test
    void cronTokensAreUnique() {
        Check a = new Check();
        Check b = new Check();
        rules.apply(a, cron(60, 0));
        rules.apply(b, cron(60, 0));
        assertThat(a.getPingToken()).isNotEqualTo(b.getPingToken());
    }

    private static CreateCheckRequest domain(String host, Integer interval) {
        return new CreateCheckRequest(CheckType.DOMAIN, "Domain", null, null, null, null, interval, null, null,
                host, null, null, null, null);
    }

    @Test
    void domainDefaultsToDailyWithAThirtyDayWarning() {
        Check c = new Check();
        rules.apply(c, domain("labelminara.com", null));
        assertThat(c.getConfig()).containsEntry("domain", "labelminara.com").containsEntry("warnDays", 30);
        assertThat(c.getTarget()).isEqualTo("domain://labelminara.com");
        assertThat(c.getSite()).isEqualTo("labelminara.com");
        assertThat(c.getIntervalSeconds()).isEqualTo(86400);
        assertThat(c.getFailureThreshold()).isEqualTo(1);
    }

    @Test
    void domainTrimsAPastedUrlDownToTheRegisteredName() {
        assertThat(CheckTypeRules.normalizeDomain("https://www.LabelMinara.com/shop?x=1")).isEqualTo("labelminara.com");
        assertThat(CheckTypeRules.normalizeDomain(" example.co.in. ")).isEqualTo("example.co.in");
        assertThat(CheckTypeRules.normalizeDomain("example.com:443")).isEqualTo("example.com");
    }

    @Test
    void domainRejectsNonDomainsAndHourlyPlusRates() {
        assertThatThrownBy(() -> rules.apply(new Check(), domain("localhost", null)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("domain");
        assertThatThrownBy(() -> rules.apply(new Check(), domain("10.0.0.1", null)))
                .isInstanceOf(InvalidCheckException.class);
        assertThatThrownBy(() -> rules.apply(new Check(), domain("bad_name.com", null)))
                .isInstanceOf(InvalidCheckException.class);
        assertThatThrownBy(() -> rules.apply(new Check(), domain("example.com", 60)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("once an hour");
    }

    @Test
    void cronRequiresAPeriod() {
        assertThatThrownBy(() -> rules.apply(new Check(), cron(null, null)))
                .isInstanceOf(InvalidCheckException.class).hasMessageContaining("periodSeconds");
    }
}
