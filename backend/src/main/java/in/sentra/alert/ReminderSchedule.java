package in.sentra.alert;

import java.time.Duration;

/** Widening reminder backoff for an open incident: 15, 30, 60 minutes apart, then hourly. */
public final class ReminderSchedule {

    private static final int[] GAPS_MINUTES = {15, 30, 60};

    private ReminderSchedule() {
    }

    /** Time after the incident opened at which reminder number n (1-based) is due. */
    public static Duration dueAfterOpen(int n) {
        long minutes = 0;
        for (int i = 0; i < n; i++) {
            minutes += i < GAPS_MINUTES.length ? GAPS_MINUTES[i] : 60;
        }
        return Duration.ofMinutes(minutes);
    }
}
