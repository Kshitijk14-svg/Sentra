package in.sentra.heartbeat;

import in.sentra.check.Check;
import in.sentra.check.CheckRepository;
import in.sentra.check.CheckRun;
import in.sentra.check.CheckRunRepository;
import in.sentra.incident.IncidentService;
import in.sentra.probe.CheckType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

/**
 * Public (no JWT) endpoint a scheduled job calls. The token in the path is the only credential,
 * so an unknown token gets a plain 404 and nothing else is revealed. A success ping records the
 * time and makes the check due at once, so the probe turns it into a run on the next scheduler
 * tick; a failure ping opens the incident right away rather than waiting for that tick.
 */
@RestController("heartbeatPingController") // common.PingController already takes the default bean name
@RequestMapping("/ping")
public class PingController {

    private final CheckRepository checks;
    private final CheckRunRepository runs;
    private final IncidentService incidents;
    private final Clock clock;

    public PingController(CheckRepository checks, CheckRunRepository runs, IncidentService incidents, Clock clock) {
        this.checks = checks;
        this.runs = runs;
        this.incidents = incidents;
        this.clock = clock;
    }

    @RequestMapping(path = "/{token}", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD})
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public String success(@PathVariable String token) {
        Check check = find(token);
        Instant now = clock.instant();
        check.setLastPingAt(now);
        check.setLastPingStatus("SUCCESS");
        // Evaluate on the next scheduler tick: records the run and lets a DOWN job recover within seconds.
        // After that, CronProbe.nextRunAt schedules the next evaluation at the new deadline.
        check.setNextRunAt(now);
        checks.save(check);
        return "OK";
    }

    @RequestMapping(path = "/{token}/start", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD})
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public String start(@PathVariable String token) {
        Check check = find(token);
        check.setLastStartAt(clock.instant());
        checks.save(check);
        return "OK";
    }

    @RequestMapping(path = "/{token}/fail", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.HEAD})
    @ResponseStatus(HttpStatus.OK)
    public String fail(@PathVariable String token) {
        Check check = markFailed(token);
        incidents.record(check.getId(), false, "Job reported a failure", false);
        return "OK";
    }

    private Check markFailed(String token) {
        Check check = find(token);
        Instant now = clock.instant();
        check.setLastPingAt(now);
        check.setLastPingStatus("FAIL");
        checks.save(check);
        CheckRun run = new CheckRun();
        run.setCheckId(check.getId());
        run.setStartedAt(now);
        run.setSuccess(false);
        run.setLatencyMs(0);
        run.setError("Job reported a failure");
        run.setDetail("Failure ping received");
        runs.save(run);
        return check;
    }

    private Check find(String token) {
        return checks.findByPingToken(token)
                .filter(c -> c.getType() == CheckType.CRON && c.isEnabled())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
