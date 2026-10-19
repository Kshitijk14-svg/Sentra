package in.sentra.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://127.0.0.1:8080/admin",
            "http://169.254.169.254/latest/meta-data",
            "http://10.0.0.1",
            "http://192.168.1.1",
            "http://172.16.0.5",
            "http://[::1]",
            "http://[fd00::1]",
            "http://100.64.0.1",
            "http://0.0.0.0",
            "http://[::ffff:127.0.0.1]",
            "http://[::127.0.0.1]",
            "http://[64:ff9b::7f00:1]",
            "http://[2002:7f00:1::1]",
            "http://[2002:a9fe:a9fe::1]",
            "http://[2001:0:4136:e378:8000:63bf:3fff:fdd2]",
            "http://[2001:db8::1]",
            "http://192.0.0.8",
            "http://198.18.0.1",
            "http://240.0.0.1",
            "http://255.255.255.255",
            "http://user:pass@93.184.216.34",
            "file:///etc/passwd",
            "ftp://93.184.216.34",
            "not a url"
    })
    void rejectsInternalAndNonHttpTargets(String url) {
        assertThatThrownBy(() -> guard.validate(url)).isInstanceOf(SsrfException.class);
    }

    @Test
    void acceptsPublicAddress() {
        assertThatCode(() -> guard.validate("http://93.184.216.34/health")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("http://[2606:2800:220:1:248:1893:25c8:1946]/")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate("http://[2002:5db8:d822::1]/")).doesNotThrowAnyException(); // 6to4 of a public IPv4
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "169.254.169.254", "10.0.0.1", "::1", "100.64.0.1"})
    void validateHostRejectsInternalHosts(String host) {
        assertThatThrownBy(() -> guard.validateHost(host)).isInstanceOf(SsrfException.class);
    }

    @Test
    void validateHostAcceptsPublicAndRejectsUnresolvable() {
        assertThatCode(() -> guard.validateHost("93.184.216.34")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.validateHost("this-host-does-not-exist.invalid"))
                .isInstanceOf(HostNotFoundException.class);
    }

    @Test
    void resolvePublicReturnsTheVettedAddresses() {
        assertThat(guard.resolvePublic("93.184.216.34")).hasSize(1);
    }

    @Test
    void allowPrivateFlagPermitsLoopbackButStillRequiresHttp() {
        SsrfGuard lax = new SsrfGuard(true);
        assertThatCode(() -> lax.validate("http://localhost:8080")).doesNotThrowAnyException();
        assertThatThrownBy(() -> lax.validate("file:///etc/passwd")).isInstanceOf(SsrfException.class);
    }
}
