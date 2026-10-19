package in.sentra.alert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_id")
    private Long checkId;

    private String channel = "WEBHOOK";

    @Column(name = "target_url")
    private String targetUrl;

    private boolean enabled = true;
}
