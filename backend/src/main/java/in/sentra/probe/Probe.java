package in.sentra.probe;

import in.sentra.check.Check;

import java.time.Instant;

public interface Probe {

    CheckType supports();

    ProbeResult execute(Check check);

    /** When the scheduler should run this check next, given that it is being dispatched now. */
    default Instant nextRunAt(Check check, Instant now) {
        return now.plusSeconds(check.getIntervalSeconds());
    }
}
