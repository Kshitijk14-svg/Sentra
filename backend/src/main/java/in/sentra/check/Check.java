package in.sentra.check;

import jakarta.persistence.*;
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
    private String type = "HTTP";
    private String target;

    @Column(name = "expected_status")
    private int expectedStatus = 200;

    @Column(name = "timeout_ms")
    private int timeoutMs = 10000;

    @Column(name = "interval_seconds")
    private int intervalSeconds = 60;

    private boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
