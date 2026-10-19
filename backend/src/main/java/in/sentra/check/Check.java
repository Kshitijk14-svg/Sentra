package in.sentra.check;

import jakarta.persistence.*;
import in.sentra.incident.CheckState;
import in.sentra.probe.CheckType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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
