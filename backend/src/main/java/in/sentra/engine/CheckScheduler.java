package in.sentra.engine;

import in.sentra.check.Check;
import in.sentra.check.CheckRepository;
import in.sentra.check.CheckService;
import in.sentra.probe.ProbeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class CheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final CheckRepository checks;
    private final CheckService service;
    private final ProbeRegistry probes;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;

    public CheckScheduler(CheckRepository checks,
                          CheckService service,
                          ProbeRegistry probes,
                          @Qualifier("probeExecutor") ThreadPoolTaskExecutor executor,
                          TransactionTemplate tx,
                          Clock clock,
                          @Value("${sentra.scheduler.batch-size:50}") int batchSize) {
        this.checks = checks;
        this.service = service;
        this.probes = probes;
        this.executor = executor;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${sentra.scheduler.tick-ms:5000}")
    public void tick() {
        try {
            for (Check check : claimDue()) {
                dispatch(check);
            }
        } catch (RuntimeException e) {
            // A failed tick must never kill the schedule.
            log.error("Scheduler tick failed", e);
        }
    }

    /**
     * Claims due checks by advancing next_run_at inside a short transaction, before any probe runs
     * (Doc 1 §6.1). SKIP LOCKED lets several instances claim disjoint rows.
     */
    private List<Check> claimDue() {
        return tx.execute(status -> {
            Instant now = clock.instant();
            List<Check> due = checks.findDueForUpdate(now, batchSize);
            for (Check check : due) {
                // Usually now + interval; a type can choose its own moment (CRON: its ping deadline).
                check.setNextRunAt(probes.get(check.getType()).nextRunAt(check, now));
            }
            return checks.saveAll(due);
        });
    }

    private void dispatch(Check check) {
        try {
            executor.execute(() -> {
                try {
                    service.runNow(check);
                } catch (RuntimeException e) {
                    log.error("Run failed for check {}", check.getId(), e);
                }
            });
        } catch (TaskRejectedException e) {
            // next_run_at is already advanced, so this run is skipped rather than retried in a hot loop.
            log.warn("Probe queue full; skipped run of check {}", check.getId());
        }
    }
}
