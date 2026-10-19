package in.sentra.check;

import in.sentra.incident.IncidentService;
import in.sentra.probe.ProbeRegistry;
import in.sentra.probe.ProbeResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class CheckService {

    private final CheckRunRepository runs;
    private final ProbeRegistry probes;
    private final IncidentService incidents;
    private final Clock clock;

    public CheckService(CheckRunRepository runs, ProbeRegistry probes, IncidentService incidents, Clock clock) {
        this.runs = runs;
        this.probes = probes;
        this.incidents = incidents;
        this.clock = clock;
    }

    public CheckRun runNow(Check check) {
        Instant startedAt = clock.instant();
        ProbeResult result;
        try {
            result = probes.get(check.getType()).execute(check);
        } catch (RuntimeException e) {
            result = new ProbeResult(false, null, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        CheckRun run = new CheckRun();
        run.setCheckId(check.getId());
        run.setStartedAt(startedAt);
        run.setSuccess(result.success());
        run.setStatusCode(result.statusCode());
        run.setLatencyMs(result.latencyMs());
        run.setError(result.error());
        CheckRun saved = runs.save(run);
        incidents.record(check.getId(), result.success(), result.error());
        return saved;
    }
}
