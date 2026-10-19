package in.sentra.alert;

import in.sentra.check.CheckRepository;
import in.sentra.security.SsrfGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checks/{checkId}/alert-rules")
public class AlertRuleController {

    public record CreateRuleRequest(
            @NotBlank @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String url) {
    }

    private final AlertRuleRepository rules;
    private final CheckRepository checks;
    private final SsrfGuard ssrfGuard;

    public AlertRuleController(AlertRuleRepository rules, CheckRepository checks, SsrfGuard ssrfGuard) {
        this.rules = rules;
        this.checks = checks;
        this.ssrfGuard = ssrfGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRule create(@AuthenticationPrincipal Long userId, @PathVariable Long checkId,
                            @Valid @RequestBody CreateRuleRequest req) {
        requireOwnedCheck(checkId, userId);
        ssrfGuard.validate(req.url());
        AlertRule rule = new AlertRule();
        rule.setCheckId(checkId);
        rule.setTargetUrl(req.url());
        try {
            return rules.save(rule);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Check already has a webhook rule");
        }
    }

    @GetMapping
    public List<AlertRule> list(@AuthenticationPrincipal Long userId, @PathVariable Long checkId) {
        requireOwnedCheck(checkId, userId);
        return rules.findByCheckId(checkId);
    }

    private void requireOwnedCheck(Long checkId, Long userId) {
        checks.findByIdForUser(checkId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Check not found"));
    }
}
