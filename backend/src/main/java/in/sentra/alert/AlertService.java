package in.sentra.alert;

import in.sentra.check.Check;
import in.sentra.check.CheckRepository;
import in.sentra.incident.Incident;
import in.sentra.incident.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleRepository rules;
    private final AlertDeliveryRepository deliveries;
    private final IncidentRepository incidents;
    private final CheckRepository checks;
    private final Map<String, Notifier> notifiers;

    public AlertService(AlertRuleRepository rules, AlertDeliveryRepository deliveries,
                        IncidentRepository incidents, CheckRepository checks, List<Notifier> notifiers) {
        this.rules = rules;
        this.deliveries = deliveries;
        this.incidents = incidents;
        this.checks = checks;
        this.notifiers = notifiers.stream().collect(Collectors.toMap(Notifier::channel, Function.identity()));
    }

    /** Sends the alert for one transition to every enabled rule of the incident's check. */
    public void dispatch(Long incidentId, String type) {
        Incident incident = incidents.findById(incidentId).orElse(null);
        if (incident == null) {
            return;
        }
        Check check = checks.findById(incident.getCheckId()).orElse(null);
        if (check == null) {
            return;
        }
        AlertMessage message = new AlertMessage(type, incidentId, check.getId(), check.getName(),
                check.getTarget(), incident.getLastError(), incident.getOpenedAt(), incident.getClosedAt());
        for (AlertRule rule : rules.findByCheckIdAndEnabledTrue(check.getId())) {
            send(rule, message);
        }
    }

    private void send(AlertRule rule, AlertMessage message) {
        Notifier notifier = notifiers.get(rule.getChannel());
        if (notifier == null) {
            log.warn("No notifier for channel {}", rule.getChannel());
            return;
        }
        // Claim first: the unique index decides who sends, so a duplicate is blocked by the database.
        if (deliveries.claim(message.incidentId(), rule.getChannel(), message.type()) == 0) {
            return;
        }
        try {
            notifier.send(rule.getTargetUrl(), message);
            deliveries.mark(message.incidentId(), rule.getChannel(), message.type(), "SENT", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deliveries.mark(message.incidentId(), rule.getChannel(), message.type(), "FAILED", "Interrupted");
        } catch (Exception e) {
            log.warn("Alert {} for incident {} failed: {}", message.type(), message.incidentId(), e.toString());
            deliveries.mark(message.incidentId(), rule.getChannel(), message.type(), "FAILED", e.getMessage());
        }
    }
}
