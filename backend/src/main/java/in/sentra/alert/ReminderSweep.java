package in.sentra.alert;

import in.sentra.incident.Incident;
import in.sentra.incident.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class ReminderSweep {

    private static final Logger log = LoggerFactory.getLogger(ReminderSweep.class);

    private final IncidentRepository incidents;
    private final AlertDeliveryRepository deliveries;
    private final AlertService alerts;
    private final Clock clock;

    public ReminderSweep(IncidentRepository incidents, AlertDeliveryRepository deliveries,
                         AlertService alerts, Clock clock) {
        this.incidents = incidents;
        this.deliveries = deliveries;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sentra.alerts.reminder-sweep-ms:60000}")
    public void sweep() {
        try {
            Instant now = clock.instant();
            for (Incident incident : incidents.findByClosedAtIsNull()) {
                int next = deliveries.countReminders(incident.getId()) + 1;
                if (!now.isBefore(incident.getOpenedAt().plus(ReminderSchedule.dueAfterOpen(next)))) {
                    alerts.dispatch(incident.getId(), "REMINDER_" + next);
                }
            }
        } catch (RuntimeException e) {
            log.error("Reminder sweep failed", e);
        }
    }
}
