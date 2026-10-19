package in.sentra.check;

import in.sentra.probe.CheckType;
import org.springframework.stereotype.Component;

import java.net.URI;
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

    public static final int MIN_INTERVAL_SECONDS = 10;

    public void apply(Check check, CreateCheckRequest r) {
        CheckType type = r.type() == null ? CheckType.HTTP : r.type();
        check.setType(type);
        check.setName(r.name().trim());
        switch (type) {
            case HTTP -> http(check, r);
            case TCP -> tcp(check, r);
        }
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
        String host = r.host() == null ? "" : r.host().trim();
        if (!HOST.matcher(host).matches()) {
            throw new InvalidCheckException("host: must be a hostname or IP address");
        }
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
