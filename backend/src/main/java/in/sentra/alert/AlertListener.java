package in.sentra.alert;

import in.sentra.incident.IncidentClosedEvent;
import in.sentra.incident.IncidentOpenedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AlertListener {

    private final AlertService alerts;

    public AlertListener(AlertService alerts) {
        this.alerts = alerts;
    }

    // After commit so the incident row is visible; async so a slow webhook never holds a probe thread.
    @Async("alertExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpened(IncidentOpenedEvent event) {
        alerts.dispatch(event.incidentId(), "OPENED");
    }

    @Async("alertExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClosed(IncidentClosedEvent event) {
        alerts.dispatch(event.incidentId(), "CLOSED");
    }
}
