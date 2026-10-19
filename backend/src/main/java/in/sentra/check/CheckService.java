package in.sentra.check;

import in.sentra.incident.CheckState;
import in.sentra.incident.IncidentService;
import in.sentra.probe.CheckType;
import in.sentra.probe.ProbeRegistry;
import in.sentra.probe.ProbeResult;
import in.sentra.project.ProjectRepository;
import in.sentra.security.SsrfGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

@Service
public class CheckService {

    private final CheckRepository checks;
    private final CheckRunRepository runs;
    private final ProbeRegistry probes;
    private final IncidentService incidents;
    private final ProjectRepository projects;
    private final CheckTypeRules rules;
    private final SsrfGuard ssrfGuard;
    private final Clock clock;

    public CheckService(CheckRepository checks, CheckRunRepository runs, ProbeRegistry probes,
                        IncidentService incidents, ProjectRepository projects, CheckTypeRules rules,
                        SsrfGuard ssrfGuard, Clock clock) {
        this.checks = checks;
        this.runs = runs;
        this.probes = probes;
        this.incidents = incidents;
        this.projects = projects;
        this.rules = rules;
        this.ssrfGuard = ssrfGuard;
        this.clock = clock;
    }

    public Check create(Long userId, CreateCheckRequest request) {
        Check check = new Check();
        rules.apply(check, request);
        guardTarget(check);
        check.setProjectId(projects.findFirstByUserIdOrderByIdAsc(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "User has no project")).getId());
        return checks.save(check);
    }

    /** Rejects internal targets at creation; each probe re-checks right before it connects. */
    private void guardTarget(Check check) {
        if (check.getType() == CheckType.HTTP) {
            ssrfGuard.validate(check.getTarget());
        } else {
            ssrfGuard.validateHost(String.valueOf(check.getConfig().get("host")));
        }
    }

    public CheckRun runNow(Check check) {
        Instant startedAt = clock.instant();
        ProbeResult result;
        try {
            result = probes.get(check.getType()).execute(check);
        } catch (RuntimeException e) {
            result = ProbeResult.fail(0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        CheckRun run = new CheckRun();
        run.setCheckId(check.getId());
        run.setStartedAt(startedAt);
        run.setSuccess(result.success());
        run.setStatusCode(result.statusCode());
        run.setLatencyMs(result.latencyMs());
        run.setError(result.error());
        run.setDetail(result.detail());
        run.setInconclusive(result.inconclusive());
        CheckRun saved = runs.save(run);
        incidents.record(check.getId(), result.success(), result.error(), result.inconclusive());
        return saved;
    }

    @Transactional
    public void pause(Check check) {
        check.setEnabled(false);
        check.setState(CheckState.PAUSED);
        checks.save(check);
        incidents.closeOpenIncident(check.getId());
    }

    @Transactional
    public void resume(Check check) {
        check.setEnabled(true);
        check.setState(CheckState.PENDING);
        check.setConsecutiveFailures(0);
        check.setConsecutiveSuccesses(0);
        check.setNextRunAt(clock.instant());
        checks.save(check);
    }

    public void delete(Check check) {
        checks.delete(check); // runs, incidents and alert rules go with it via ON DELETE CASCADE
    }
}
