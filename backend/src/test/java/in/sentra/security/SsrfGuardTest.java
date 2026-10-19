package in.sentra.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    }

    @Test
    void allowPrivateFlagPermitsLoopbackButStillRequiresHttp() {
        SsrfGuard lax = new SsrfGuard(true);
        assertThatCode(() -> lax.validate("http://localhost:8080")).doesNotThrowAnyException();
        assertThatThrownBy(() -> lax.validate("file:///etc/passwd")).isInstanceOf(SsrfException.class);
    }
}
