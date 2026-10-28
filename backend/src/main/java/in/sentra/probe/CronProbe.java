package in.sentra.probe;

import in.sentra.check.Check;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Dead-man's switch for a scheduled job. The job pushes pings to /ping/{token}; this probe never
 * touches the network, it only checks that the last ping is recent enough (period + grace) and
 * that the job did not report a failure.
 */
@Component
public class CronProbe implements Probe {

    public static final int DEFAULT_GRACE_SECONDS = 300;

    private final Clock clock;

    public CronProbe(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CheckType supports() {
        return CheckType.CRON;
    }

    @Override
    public ProbeResult execute(Check check) {
        long period = number(check, "periodSeconds", 3600);
        long grace = number(check, "graceSeconds", DEFAULT_GRACE_SECONDS);
        return assess(check.getLastPingAt(), check.getLastPingStatus(), check.getCreatedAt(),
                clock.instant(), period, grace);
    }

    /** Pure rule: on time, failed, late, or still waiting for the first ping. */
    static ProbeResult assess(Instant lastPingAt, String lastStatus, Instant createdAt, Instant now,
                              long periodSeconds, long graceSeconds) {
        Duration deadline = Duration.ofSeconds(periodSeconds + graceSeconds);
        if (lastPingAt == null) {
            if (createdAt == null || Duration.between(createdAt, now).compareTo(deadline) <= 0) {
                return ProbeResult.ok(0, "Waiting for the first ping");
            }
            return ProbeResult.fail(0, "No ping received since the check was created, expected every "
                    + human(periodSeconds));
        }
        Duration since = Duration.between(lastPingAt, now);
        if ("FAIL".equals(lastStatus)) {
            return ProbeResult.fail(0, "Job reported a failure " + human(since.toSeconds()) + " ago");
        }
        if (since.compareTo(deadline) > 0) {
            return ProbeResult.fail(0, "No ping for " + human(since.toSeconds()) + ", expected every "
                    + human(periodSeconds) + " (+" + human(graceSeconds) + " grace)");
        }
        return ProbeResult.ok(0, "Last ping " + human(since.toSeconds()) + " ago");
    }

    static String human(long seconds) {
        if (seconds < 120) {
            return seconds + "s";
        }
        if (seconds < 7200) {
            return seconds / 60 + " min";
        }
        if (seconds < 172800) {
            return seconds / 3600 + " h";
        }
        return seconds / 86400 + " days";
    }

    private static long number(Check check, String key, long fallback) {
        return check.getConfig().get(key) instanceof Number n ? n.longValue() : fallback;
    }
}
