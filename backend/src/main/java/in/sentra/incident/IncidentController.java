package in.sentra.incident;

import in.sentra.check.CheckRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class IncidentController {

    private final IncidentRepository incidents;
    private final CheckRepository checks;

    public IncidentController(IncidentRepository incidents, CheckRepository checks) {
        this.incidents = incidents;
        this.checks = checks;
    }

    @GetMapping("/incidents")
    public List<Incident> list(@AuthenticationPrincipal Long userId) {
        return incidents.findRecentForUser(userId, Pageable.ofSize(100));
    }

    @GetMapping("/checks/{id}/incidents")
    public List<Incident> forCheck(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        checks.findByIdForUser(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check not found"));
        return incidents.findByCheckIdOrderByOpenedAtDesc(id);
    }
}
