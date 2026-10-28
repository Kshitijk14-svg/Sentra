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

    /**
     * Grace scales with the schedule: 20% of the period, between 10 seconds and 1 hour. A 30 s job
     * may be 10 s late, a 15 min job 3 min, a daily job 1 hour. Below 10 s, network jitter and the
     * scheduler tick (5 s) would cause false alarms; above 1 hour, a dead job goes unnoticed too long.
     * The frontend uses the same rule to pre-fill the form (Dashboard.jsx: defaultGrace).
     */
    public static int defaultGrace(int periodSeconds) {
        return Math.max(10, Math.min(3600, Math.round(periodSeconds * 0.2f)));
    }

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

    /**
     * Evaluate only when something can have changed, instead of on a fixed timer: a ping already
     * triggers an immediate run (PingController), so the only other moment that matters is the
     * deadline. A daily job therefore costs about one run a day, not one every 30 seconds.
     */
    @Override
    public Instant nextRunAt(Check check, Instant now) {
        return nextCheck(check.getLastPingAt(), check.getCreatedAt(), now,
                number(check, "periodSeconds", 3600), number(check, "graceSeconds", DEFAULT_GRACE_SECONDS));
    }

    /**
     * Pure rule: one second after the current deadline (last ping, or creation, plus period plus grace).
     * Once already late the incident is open, so re-evaluate only once per period until a ping arrives.
     */
    static Instant nextCheck(Instant lastPingAt, Instant createdAt, Instant now, long periodSeconds, long graceSeconds) {
        Instant since = lastPingAt != null ? lastPingAt : createdAt;
        if (since != null) {
            Instant deadline = since.plusSeconds(periodSeconds + graceSeconds + 1);
            if (deadline.isAfter(now)) {
                return deadline;
            }
        }
        return now.plusSeconds(Math.max(periodSeconds, 60));
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
