package in.sentra.check;

import jakarta.persistence.*;
import in.sentra.incident.CheckState;
import in.sentra.probe.CheckType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "checks")
@Getter
@Setter
@NoArgsConstructor
public class Check {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    private String name;
    @Enumerated(EnumType.STRING)
    private CheckType type = CheckType.HTTP;
    private String target;

    /** Grouping key for the dashboard cards (usually the website's hostname). */
    private String site;

    /**
     * Type-specific settings. Keys ending in "Enc" hold encrypted secrets and must never be sent
     * to clients (see CheckResponse).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> config = new HashMap<>();

    @Column(name = "expected_status")
    private int expectedStatus = 200;

    @Column(name = "timeout_ms")
    private int timeoutMs = 10000;

    @Column(name = "interval_seconds")
    private int intervalSeconds = 60;

    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    private CheckState state = CheckState.PENDING;

    @Column(name = "consecutive_failures")
    private int consecutiveFailures;

    @Column(name = "consecutive_successes")
    private int consecutiveSuccesses;

    @Column(name = "failure_threshold")
    private int failureThreshold = 3;

    @Column(name = "recovery_threshold")
    private int recoveryThreshold = 2;

    @Column(name = "next_run_at")
    private Instant nextRunAt = Instant.now();

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
