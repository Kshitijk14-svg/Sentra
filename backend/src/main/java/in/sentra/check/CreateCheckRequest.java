package in.sentra.check;

import in.sentra.probe.CheckType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One request shape for every check type; which fields are required depends on {@code type} and is
 * enforced by {@link CheckTypeRules}. A missing type means HTTP, so older clients keep working.
 */
public record CreateCheckRequest(
        CheckType type,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 253) String site,
        @Size(max = 2000) String target,
        @Min(100) @Max(599) Integer expectedStatus,
        @Min(500) @Max(60000) Integer timeoutMs,
        @Min(1) Integer intervalSeconds,
        @Min(1) @Max(20) Integer failureThreshold,
        @Min(1) @Max(20) Integer recoveryThreshold,
        @Size(max = 253) String host,
        @Min(1) @Max(65535) Integer port,
        @Min(1) @Max(365) Integer warnDays,
        @Min(10) @Max(31_536_000) Integer periodSeconds,
        @Min(0) @Max(604_800) Integer graceSeconds) {
}
