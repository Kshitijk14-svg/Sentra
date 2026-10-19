package in.sentra.probe;

import in.sentra.check.Check;
import in.sentra.security.HostNotFoundException;
import in.sentra.security.SsrfException;
import in.sentra.security.SsrfGuard;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Certificate health: does the TLS handshake succeed with a trusted, hostname-matching
 * certificate, and is it far enough from expiry? The handshake uses the JVM's default trust
 * store and hostname verification, so an untrusted or mismatched certificate fails the run.
 * A certificate inside the warning window fails too, so the normal incident and webhook path
 * warns before it actually lapses. Connects to an address the SSRF guard already vetted.
 */
@Component
public class TlsProbe implements Probe {

    static final int MAX_ADDRESSES = 3;
    public static final int DEFAULT_WARN_DAYS = 14;

    private final SsrfGuard ssrfGuard;
    private final Clock clock;

    public TlsProbe(SsrfGuard ssrfGuard, Clock clock) {
        this.ssrfGuard = ssrfGuard;
        this.clock = clock;
    }

    @Override
    public CheckType supports() {
        return CheckType.TLS;
    }

    @Override
    public ProbeResult execute(Check check) {
        String host = String.valueOf(check.getConfig().get("host"));
        int port = ((Number) check.getConfig().get("port")).intValue();
        int warnDays = check.getConfig().get("warnDays") instanceof Number n ? n.intValue() : DEFAULT_WARN_DAYS;
        long start = System.nanoTime();
        try {
            InetAddress[] addresses = ssrfGuard.resolvePublic(host);
            long deadline = start + check.getTimeoutMs() * 1_000_000L;
            IOException last = null;
            for (int i = 0; i < Math.min(addresses.length, MAX_ADDRESSES); i++) {
                int remainingMs = (int) ((deadline - System.nanoTime()) / 1_000_000);
                if (remainingMs <= 0) {
                    last = new SocketTimeoutException();
                    break;
                }
                try {
                    X509Certificate leaf = handshake(host, addresses[i], port, remainingMs);
                    ProbeResult verdict = assess(leaf.getNotAfter().toInstant(), clock.instant(), warnDays,
                            leaf.getSubjectX500Principal().getName());
                    return new ProbeResult(verdict.success(), null, elapsedMs(start), verdict.error(),
                            verdict.detail(), false);
                } catch (IOException e) {
                    last = e;
                    if (isCertificateProblem(e)) {
                        break; // another address would present the same certificate
                    }
                }
            }
            return ProbeResult.fail(elapsedMs(start), describe(last, port, check.getTimeoutMs()));
        } catch (HostNotFoundException e) {
            return ProbeResult.fail(elapsedMs(start), "Host not found: the DNS lookup failed");
        } catch (SsrfException e) {
            return ProbeResult.fail(elapsedMs(start), "Target not allowed: " + e.getMessage());
        }
    }

    /** Opens TCP to the vetted address, then handshakes using the hostname for SNI and verification. */
    private static X509Certificate handshake(String host, InetAddress address, int port, int budgetMs)
            throws IOException {
        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(address, port), budgetMs);
            raw.setSoTimeout(budgetMs);
            try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(raw, host, port, true)) {
                SSLParameters params = tls.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                tls.setSSLParameters(params);
                tls.startHandshake();
                Certificate[] chain = tls.getSession().getPeerCertificates();
                return (X509Certificate) chain[0];
            }
        }
    }

    /** Pure expiry rule: healthy, expiring within the warning window, or already expired. */
    static ProbeResult assess(Instant notAfter, Instant now, int warnDays, String subject) {
        long daysLeft = Duration.between(now, notAfter).toDays();
        String when = notAfter.toString().substring(0, 10);
        if (notAfter.isBefore(now)) {
            return ProbeResult.fail(0, "Certificate expired " + Math.max(1, -daysLeft) + " day(s) ago (" + when + ")");
        }
        if (daysLeft < warnDays) {
            return ProbeResult.fail(0, "Certificate expires in " + daysLeft + " day(s) (" + when
                    + "), inside the " + warnDays + "-day warning window");
        }
        return ProbeResult.ok(0, "Certificate valid for " + daysLeft + " more days (until " + when + "), " + subject);
    }

    static boolean isCertificateProblem(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    static String describe(IOException e, int port, int timeoutMs) {
        if (e == null) {
            return "Could not connect: unknown error";
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof CertificateExpiredException) {
                return "Certificate has expired";
            }
            if (t instanceof CertificateNotYetValidException) {
                return "Certificate is not valid yet";
            }
        }
        if (isCertificateProblem(e)) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return "Certificate not trusted: " + root.getMessage();
        }
        if (e instanceof SocketTimeoutException) {
            return "Timed out after " + timeoutMs + " ms during the TLS handshake";
        }
        if (e instanceof ConnectException && e.getMessage() != null
                && e.getMessage().toLowerCase().contains("refused")) {
            return "Connection refused: nothing is listening on port " + port;
        }
        return "TLS handshake failed: " + e.getMessage();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
