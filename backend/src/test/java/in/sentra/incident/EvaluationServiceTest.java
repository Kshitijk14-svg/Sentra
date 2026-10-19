package in.sentra.incident;

import in.sentra.incident.EvaluationService.Outcome;
import in.sentra.incident.EvaluationService.Transition;
import org.junit.jupiter.api.Test;

import static in.sentra.incident.CheckState.*;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluationServiceTest {

    private final EvaluationService svc = new EvaluationService();

    @Test
    void firstSuccessMakesPendingCheckHealthy() {
        Outcome o = svc.evaluate(PENDING, 0, 0, 3, 2, true);
        assertThat(o.state()).isEqualTo(HEALTHY);
        assertThat(o.transition()).isEqualTo(Transition.NONE);
    }

    @Test
    void failuresBelowThresholdAreDegradedAndOpenNothing() {
        Outcome o = svc.evaluate(HEALTHY, 0, 5, 3, 2, false);
        assertThat(o.state()).isEqualTo(DEGRADED);
        assertThat(o.consecutiveFailures()).isEqualTo(1);
        assertThat(o.transition()).isEqualTo(Transition.NONE);
    }

    @Test
    void nthConsecutiveFailureOpensIncident() {
        Outcome o = svc.evaluate(DEGRADED, 2, 0, 3, 2, false);
        assertThat(o.state()).isEqualTo(DOWN);
        assertThat(o.transition()).isEqualTo(Transition.OPENED);
    }

    @Test
    void successInterruptsFailureStreakSoNoIncidentOpens() {
        Outcome o = svc.evaluate(DEGRADED, 2, 0, 3, 2, true);
        assertThat(o.state()).isEqualTo(HEALTHY);
        assertThat(o.consecutiveFailures()).isZero();
        assertThat(o.transition()).isEqualTo(Transition.NONE);
    }

    @Test
    void downCheckNeedsMConsecutiveSuccessesToClose() {
        Outcome first = svc.evaluate(DOWN, 4, 0, 3, 2, true);
        assertThat(first.state()).isEqualTo(DOWN);
        assertThat(first.transition()).isEqualTo(Transition.NONE);

        Outcome second = svc.evaluate(first.state(), first.consecutiveFailures(),
                first.consecutiveSuccesses(), 3, 2, true);
        assertThat(second.state()).isEqualTo(HEALTHY);
        assertThat(second.transition()).isEqualTo(Transition.CLOSED);
    }

    @Test
    void failureWhileRecoveringResetsSuccessStreak() {
        Outcome o = svc.evaluate(DOWN, 0, 1, 3, 2, false);
        assertThat(o.state()).isEqualTo(DOWN);
        assertThat(o.consecutiveSuccesses()).isZero();
        assertThat(o.transition()).isEqualTo(Transition.NONE);
    }

    @Test
    void pausedCheckIsUntouched() {
        Outcome o = svc.evaluate(PAUSED, 1, 0, 3, 2, false);
        assertThat(o.state()).isEqualTo(PAUSED);
        assertThat(o.consecutiveFailures()).isEqualTo(1);
        assertThat(o.transition()).isEqualTo(Transition.NONE);
    }
}
