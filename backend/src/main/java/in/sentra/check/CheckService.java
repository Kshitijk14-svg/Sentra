package in.sentra.check;

import in.sentra.probe.HttpProbe;
import in.sentra.probe.ProbeResult;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CheckService {

    private final CheckRunRepository runs;
    private final HttpProbe httpProbe;

    public CheckService(CheckRunRepository runs, HttpProbe httpProbe) {
        this.runs = runs;
        this.httpProbe = httpProbe;
    }

    public CheckRun runNow(Check check) {
        Instant startedAt = Instant.now();
        ProbeResult result = httpProbe.run(check);
        CheckRun run = new CheckRun();
        run.setCheckId(check.getId());
        run.setStartedAt(startedAt);
        run.setSuccess(result.success());
        run.setStatusCode(result.statusCode());
        run.setLatencyMs(result.latencyMs());
        run.setError(result.error());
        return runs.save(run);
    }
}
