package in.sentra.check;

import in.sentra.incident.CheckState;
import in.sentra.probe.CheckType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** What the API returns for a check. Entities are never serialized, so secrets cannot leak by accident. */
public record CheckResponse(
        Long id, String name, CheckType type, String site, String target, CheckState state, boolean enabled,
        int intervalSeconds, int timeoutMs, int expectedStatus, int failureThreshold, int recoveryThreshold,
        int consecutiveFailures, int consecutiveSuccesses, Instant nextRunAt, Instant createdAt,
        Map<String, Object> config) {

    public static CheckResponse from(Check c) {
        return new CheckResponse(c.getId(), c.getName(), c.getType(), c.getSite(), c.getTarget(), c.getState(),
                c.isEnabled(), c.getIntervalSeconds(), c.getTimeoutMs(), c.getExpectedStatus(),
                c.getFailureThreshold(), c.getRecoveryThreshold(), c.getConsecutiveFailures(),
                c.getConsecutiveSuccesses(), c.getNextRunAt(), c.getCreatedAt(), publicConfig(c.getConfig()));
    }

    /** Config minus secrets: by convention every secret key ends in "Enc" (encrypted at rest). */
    static Map<String, Object> publicConfig(Map<String, Object> config) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (config != null) {
            config.forEach((k, v) -> {
                if (!k.endsWith("Enc")) {
                    safe.put(k, v);
                }
            });
        }
        return safe;
    }
}
