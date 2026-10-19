package in.sentra.probe;

import in.sentra.check.Check;
import in.sentra.security.SsrfException;
import in.sentra.security.SsrfGuard;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpProbe implements Probe {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final SsrfGuard ssrfGuard;

    public HttpProbe(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    @Override
    public CheckType supports() {
        return CheckType.HTTP;
    }

    @Override
    public ProbeResult execute(Check check) {
        long start = System.nanoTime();
        try {
            // DNS may have changed since creation, so re-validate right before connecting.
            ssrfGuard.validate(check.getTarget());
            HttpRequest request = HttpRequest.newBuilder(URI.create(check.getTarget()))
                    .timeout(Duration.ofMillis(check.getTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long latency = elapsedMs(start);
            int status = response.statusCode();
            boolean ok = status == check.getExpectedStatus();
            return new ProbeResult(ok, status, latency, ok ? null : "Unexpected status " + status);
        } catch (SsrfException e) {
            return new ProbeResult(false, null, elapsedMs(start), "Target not allowed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProbeResult(false, null, elapsedMs(start), "Interrupted");
        } catch (Exception e) {
            return new ProbeResult(false, null, elapsedMs(start), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
