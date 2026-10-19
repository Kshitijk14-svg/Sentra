package in.sentra.check;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checks")
public class CheckController {

    public record CreateCheckRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String target,
            @Min(100) @Max(599) Integer expectedStatus,
            @Min(500) @Max(60000) Integer timeoutMs,
            @Min(10) Integer intervalSeconds,
            @Min(1) @Max(20) Integer failureThreshold,
            @Min(1) @Max(20) Integer recoveryThreshold) {
    }

    private final CheckRepository checks;
    private final CheckRunRepository runs;
    private final CheckService service;

    public CheckController(CheckRepository checks, CheckRunRepository runs, CheckService service) {
        this.checks = checks;
        this.runs = runs;
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Check create(@Valid @RequestBody CreateCheckRequest req) {
        Check check = new Check();
        check.setName(req.name());
        check.setTarget(req.target());
        if (req.expectedStatus() != null) check.setExpectedStatus(req.expectedStatus());
        if (req.timeoutMs() != null) check.setTimeoutMs(req.timeoutMs());
        if (req.intervalSeconds() != null) check.setIntervalSeconds(req.intervalSeconds());
        if (req.failureThreshold() != null) check.setFailureThreshold(req.failureThreshold());
        if (req.recoveryThreshold() != null) check.setRecoveryThreshold(req.recoveryThreshold());
        return checks.save(check);
    }

    @GetMapping
    public List<Check> list() {
        return checks.findAll();
    }

    @GetMapping("/{id}")
    public Check get(@PathVariable Long id) {
        return find(id);
    }

    @PostMapping("/{id}/run")
    public CheckRun run(@PathVariable Long id) {
        return service.runNow(find(id));
    }

    @GetMapping("/{id}/runs")
    public List<CheckRun> history(@PathVariable Long id) {
        find(id);
        return runs.findTop50ByCheckIdOrderByStartedAtDesc(id);
    }

    private Check find(Long id) {
        return checks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check not found"));
    }
}
