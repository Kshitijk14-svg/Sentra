package in.sentra.alert;

import in.sentra.check.CheckRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

    public AlertRuleController(AlertRuleRepository rules, CheckRepository checks) {
        this.rules = rules;
        this.checks = checks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertRule create(@PathVariable Long checkId, @Valid @RequestBody CreateRuleRequest req) {
        requireCheck(checkId);
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
    public List<AlertRule> list(@PathVariable Long checkId) {
        requireCheck(checkId);
        return rules.findByCheckId(checkId);
    }

    private void requireCheck(Long checkId) {
        if (!checks.existsById(checkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Check not found");
        }
    }
}
