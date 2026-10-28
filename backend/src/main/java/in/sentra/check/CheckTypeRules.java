package in.sentra.check;

import in.sentra.probe.CheckType;
import in.sentra.probe.CronProbe;
import in.sentra.probe.DomainProbe;
import in.sentra.probe.TlsProbe;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validates a create request for its check type and fills in that type's defaults. Pure (no I/O,
 * no Spring state) so every rule can be unit-tested; network safety (SSRF) is checked separately.
 */
@Component
public class CheckTypeRules {

    private static final Pattern HTTP_URL = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.:-]{0,251}[A-Za-z0-9])?$");

    // Letters, digits and hyphens per label, at least two labels, an alphabetic TLD (or xn-- IDN).
    private static final Pattern DOMAIN_NAME = Pattern.compile(
            "^(?=.{4,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+([a-z]{2,63}|xn--[a-z0-9-]{1,59})$");
    static final int DOMAIN_MIN_INTERVAL_SECONDS = 3600;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int MIN_INTERVAL_SECONDS = 10;

    public void apply(Check check, CreateCheckRequest r) {
        CheckType type = r.type() == null ? CheckType.HTTP : r.type();
        check.setType(type);
        check.setName(r.name().trim());
        switch (type) {
            case HTTP -> http(check, r);
            case TCP -> tcp(check, r);
            case TLS -> tls(check, r);
            case CRON -> cron(check, r);
            case DOMAIN -> domain(check, r);
        }
    }

    /**
     * Domain expiry. The domain arrives in {@code host}; a pasted URL or a leading "www." is trimmed
     * off, because the registry only knows the registered name. Registries rate-limit, and
     * registration dates change over days, so it runs daily and never more often than hourly.
     */
    private void domain(Check check, CreateCheckRequest r) {
        String domain = normalizeDomain(r.host());
        if (!DOMAIN_NAME.matcher(domain).matches()) {
            throw new InvalidCheckException("host: must be a registered domain name such as example.com");
        }
        if (r.intervalSeconds() != null && r.intervalSeconds() < DOMAIN_MIN_INTERVAL_SECONDS) {
            throw new InvalidCheckException("intervalSeconds: a domain check runs at most once an hour");
        }
        Map<String, Object> config = new HashMap<>();
        config.put("domain", domain);
        config.put("warnDays", r.warnDays() != null ? r.warnDays() : DomainProbe.DEFAULT_WARN_DAYS);
        check.setConfig(config);
        check.setTarget("domain://" + domain);
        check.setSite(blankToNull(r.site()) != null ? r.site().trim() : domain);
        common(check, r, 86400, 10000, 1, 1);
    }

    static String normalizeDomain(String input) {
        String d = input == null ? "" : input.trim().toLowerCase();
        d = d.replaceFirst("^[a-z][a-z0-9+.-]*://", ""); // scheme
        d = d.replaceFirst("[/?#].*$", "");              // path, query, fragment
        d = d.replaceFirst(":\\d+$", "");                // port
        d = d.replaceFirst("\\.$", "");                  // trailing dot
        return d.startsWith("www.") ? d.substring(4) : d;
    }

    private void http(Check check, CreateCheckRequest r) {
        if (r.target() == null || !HTTP_URL.matcher(r.target().trim()).matches()) {
            throw new InvalidCheckException("target: must be an http(s) URL");
        }
        String target = r.target().trim();
        check.setTarget(target);
        check.setSite(blankToNull(r.site()) != null ? r.site().trim() : hostOf(target));
        check.setExpectedStatus(r.expectedStatus() != null ? r.expectedStatus() : 200);
        common(check, r, 60, 10000, 3, 2);
    }

    private void tcp(Check check, CreateCheckRequest r) {
        String host = validHost(r);
        if (r.port() == null) {
            throw new InvalidCheckException("port: required for a TCP check");
        }
        Map<String, Object> config = new HashMap<>();
        config.put("host", host);
        config.put("port", r.port());
        check.setConfig(config);
        check.setTarget("tcp://" + host + ":" + r.port());
        check.setSite(blankToNull(r.site()) != null ? r.site().trim() : host);
        common(check, r, 60, 5000, 3, 2);
    }

    private void tls(Check check, CreateCheckRequest r) {
        String host = validHost(r);
        int port = r.port() != null ? r.port() : 443;
        Map<String, Object> config = new HashMap<>();
        config.put("host", host);
        config.put("port", port);
        config.put("warnDays", r.warnDays() != null ? r.warnDays() : TlsProbe.DEFAULT_WARN_DAYS);
        check.setConfig(config);
        check.setTarget("tls://" + host + ":" + port);
        check.setSite(blankToNull(r.site()) != null ? r.site().trim() : host);
        // Certificates change over days, so a slower default cadence is enough.
        common(check, r, 3600, 10000, 1, 1);
    }

    private void cron(Check check, CreateCheckRequest r) {
        if (r.periodSeconds() == null) {
            throw new InvalidCheckException("periodSeconds: required for a CRON check");
        }
        Map<String, Object> config = new HashMap<>();
        config.put("periodSeconds", r.periodSeconds());
        config.put("graceSeconds", r.graceSeconds() != null ? r.graceSeconds() : CronProbe.defaultGrace(r.periodSeconds()));
        check.setConfig(config);
        check.setPingToken(newPingToken());
        check.setTarget("cron://" + check.getName());
        check.setSite(blankToNull(r.site()) != null ? r.site().trim() : "Scheduled jobs");
        // The interval is informational: CronProbe.nextRunAt schedules the next evaluation at the
        // ping deadline (and a ping triggers one right away), not on a fixed timer.
        common(check, r, r.periodSeconds(), 5000, 1, 1);
    }

    private static String newPingToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String validHost(CreateCheckRequest r) {
        String host = r.host() == null ? "" : r.host().trim();
        if (!HOST.matcher(host).matches()) {
            throw new InvalidCheckException("host: must be a hostname or IP address");
        }
        return host;
    }

    /** Interval, timeout and thresholds shared by every type, each with its own default. */
    private void common(Check check, CreateCheckRequest r, int defaultInterval, int defaultTimeoutMs,
                        int defaultFailures, int defaultRecoveries) {
        int interval = r.intervalSeconds() != null ? r.intervalSeconds() : defaultInterval;
        if (interval < MIN_INTERVAL_SECONDS) {
            throw new InvalidCheckException("intervalSeconds: must be at least " + MIN_INTERVAL_SECONDS);
        }
        check.setIntervalSeconds(interval);
        check.setTimeoutMs(r.timeoutMs() != null ? r.timeoutMs() : defaultTimeoutMs);
        check.setFailureThreshold(r.failureThreshold() != null ? r.failureThreshold() : defaultFailures);
        check.setRecoveryThreshold(r.recoveryThreshold() != null ? r.recoveryThreshold() : defaultRecoveries);
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            throw new InvalidCheckException("target: not a valid URL");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
