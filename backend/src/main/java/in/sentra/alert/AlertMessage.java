package in.sentra.alert;

import java.time.Instant;

public record AlertMessage(String type, Long incidentId, Long checkId, String checkName,
                           String target, String error, Instant openedAt, Instant closedAt) {
}
