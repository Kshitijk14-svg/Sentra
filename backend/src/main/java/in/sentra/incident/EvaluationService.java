package in.sentra.incident;

import org.springframework.stereotype.Component;

/**
 * Pure hysteresis logic (Doc 1 §6.3-6.4): no Spring context, repositories or clock, so it is
 * unit-testable in isolation. N consecutive failures open an incident, M consecutive successes
 * close it; anything in between is DEGRADED or stays DOWN rather than flapping.
 */
@Component
public class EvaluationService {

    public enum Transition { NONE, OPENED, CLOSED }

    public record Outcome(CheckState state, int consecutiveFailures, int consecutiveSuccesses,
                          Transition transition) {
    }

    public Outcome evaluate(CheckState state, int failures, int successes,
                            int failureThreshold, int recoveryThreshold, boolean success) {
        if (state == CheckState.PAUSED) {
            return new Outcome(state, failures, successes, Transition.NONE);
        }
        int f = success ? 0 : failures + 1;
        int s = success ? successes + 1 : 0;

        if (state == CheckState.DOWN) {
            if (s >= recoveryThreshold) {
                return new Outcome(CheckState.HEALTHY, f, s, Transition.CLOSED);
            }
            return new Outcome(CheckState.DOWN, f, s, Transition.NONE);
        }
        if (f >= failureThreshold) {
            return new Outcome(CheckState.DOWN, f, s, Transition.OPENED);
        }
        return new Outcome(f > 0 ? CheckState.DEGRADED : CheckState.HEALTHY, f, s, Transition.NONE);
    }
}
