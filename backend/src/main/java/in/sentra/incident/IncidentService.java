package in.sentra.incident;

import in.sentra.check.Check;
import in.sentra.check.CheckRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class IncidentService {

    private final CheckRepository checks;
    private final IncidentRepository incidents;
    private final EvaluationService evaluation;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public IncidentService(CheckRepository checks, IncidentRepository incidents,
                           EvaluationService evaluation, ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.checks = checks;
        this.incidents = incidents;
        this.evaluation = evaluation;
        this.clock = clock;
    }

    /**
     * Applies one run result to the check's state. The check row is locked so overlapping runs of
     * the same check (e.g. timeout longer than interval) update the counters one at a time.
     */
    @Transactional
    public void record(Long checkId, boolean success, String error) {
        Check check = checks.findByIdForUpdate(checkId).orElse(null);
        if (check == null) {
            return; // deleted while the probe was in flight
        }
        EvaluationService.Outcome outcome = evaluation.evaluate(
                check.getState(), check.getConsecutiveFailures(), check.getConsecutiveSuccesses(),
                check.getFailureThreshold(), check.getRecoveryThreshold(), success);

        check.setState(outcome.state());
        check.setConsecutiveFailures(outcome.consecutiveFailures());
        check.setConsecutiveSuccesses(outcome.consecutiveSuccesses());

        switch (outcome.transition()) {
            case OPENED -> {
                Incident incident = new Incident();
                incident.setCheckId(checkId);
                incident.setOpenedAt(clock.instant());
                incident.setLastError(error);
                Incident saved = incidents.save(incident);
                events.publishEvent(new IncidentOpenedEvent(saved.getId(), checkId));
            }
            case CLOSED -> incidents.findByCheckIdAndClosedAtIsNull(checkId).ifPresent(i -> {
                i.setClosedAt(clock.instant());
                incidents.save(i);
                events.publishEvent(new IncidentClosedEvent(i.getId(), checkId));
            });
            case NONE -> {
                if (!success && check.getState() == CheckState.DOWN) {
                    incidents.findByCheckIdAndClosedAtIsNull(checkId).ifPresent(i -> {
                        i.setLastError(error);
                        incidents.save(i);
                    });
                }
            }
        }
    }
}
