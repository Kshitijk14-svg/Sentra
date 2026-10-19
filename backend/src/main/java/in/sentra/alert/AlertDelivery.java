package in.sentra.alert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "alert_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id")
    private Long incidentId;

    private String channel;

    @Column(name = "transition_type")
    private String transitionType;

    private String status;
    private String error;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
