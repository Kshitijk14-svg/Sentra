package in.sentra.check;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "check_runs")
@Getter
@Setter
@NoArgsConstructor
public class CheckRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_id")
    private Long checkId;

    @Column(name = "started_at")
    private Instant startedAt;

    private boolean success;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private long latencyMs;

    private String error;

    private String detail;

    private boolean inconclusive;
}
