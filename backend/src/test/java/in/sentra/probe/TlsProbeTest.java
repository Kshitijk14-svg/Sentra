package in.sentra.probe;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TlsProbeTest {

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    @Test
    void farFromExpiryIsHealthy() {
        ProbeResult r = TlsProbe.assess(NOW.plus(90, ChronoUnit.DAYS), NOW, 14, "CN=example.com");
        assertThat(r.success()).isTrue();
        assertThat(r.detail()).contains("90 more days").contains("CN=example.com");
    }

    @Test
    void insideTheWarningWindowFails() {
        ProbeResult r = TlsProbe.assess(NOW.plus(5, ChronoUnit.DAYS), NOW, 14, "CN=x");
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("expires in 5 day(s)").contains("14-day");
    }

    @Test
    void exactlyAtTheWindowEdgeIsStillHealthy() {
        assertThat(TlsProbe.assess(NOW.plus(14, ChronoUnit.DAYS), NOW, 14, "CN=x").success()).isTrue();
    }

    @Test
    void expiredFailsAndSaysHowLongAgo() {
        ProbeResult r = TlsProbe.assess(NOW.minus(3, ChronoUnit.DAYS), NOW, 14, "CN=x");
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("expired 3 day(s) ago");
    }

    @Test
    void handshakeErrorsAreDescribedByCause() {
        SSLHandshakeException expired = new SSLHandshakeException("PKIX path validation failed");
        expired.initCause(new CertificateExpiredException("NotAfter"));
        assertThat(TlsProbe.describe(expired, 443, 5000)).isEqualTo("Certificate has expired");

        SSLHandshakeException untrusted = new SSLHandshakeException("bad");
        untrusted.initCause(new CertificateException("unable to find valid path"));
        assertThat(TlsProbe.describe(untrusted, 443, 5000)).startsWith("Certificate not trusted");

        assertThat(TlsProbe.describe(new SocketTimeoutException(), 443, 5000)).contains("Timed out after 5000 ms");
        assertThat(TlsProbe.describe(new IOException("reset"), 443, 5000)).contains("TLS handshake failed");
    }
}
