package in.sentra.check;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checks")
public class CheckController {

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
    public CheckResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateCheckRequest req) {
        return CheckResponse.from(service.create(userId, req));
    }

    @GetMapping
    public List<CheckResponse> list(@AuthenticationPrincipal Long userId) {
        return checks.findAllForUser(userId).stream().map(CheckResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CheckResponse get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return CheckResponse.from(find(id, userId));
    }

    @PostMapping("/{id}/run")
    public CheckRun run(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return service.runNow(find(id, userId));
    }

    @GetMapping("/{id}/runs")
    public List<CheckRun> history(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        find(id, userId);
        return runs.findTop50ByCheckIdOrderByStartedAtDesc(id);
    }

    @PostMapping("/{id}/pause")
    public CheckResponse pause(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Check check = find(id, userId);
        service.pause(check);
        return CheckResponse.from(check);
    }

    @PostMapping("/{id}/resume")
    public CheckResponse resume(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Check check = find(id, userId);
        service.resume(check);
        return CheckResponse.from(check);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(find(id, userId));
    }

    private Check find(Long id, Long userId) {
        return checks.findByIdForUser(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check not found"));
    }
}
