package in.sentra.probe;

import in.sentra.check.Check;
import in.sentra.security.HostNotFoundException;
import in.sentra.security.SsrfException;
import in.sentra.security.SsrfGuard;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Port reachability (Doc 1 §5.5): can a TCP connection be opened? It says nothing about whether
 * the service behind the port works. Connects to an address the SSRF guard already vetted.
 */
@Component
public class TcpProbe implements Probe {

    /** A host can resolve to many addresses; trying them all would stretch one run far past its timeout. */
    static final int MAX_ADDRESSES = 3;

    private final SsrfGuard ssrfGuard;

    public TcpProbe(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    @Override
    public CheckType supports() {
        return CheckType.TCP;
    }

    @Override
    public ProbeResult execute(Check check) {
        String host = String.valueOf(check.getConfig().get("host"));
        int port = ((Number) check.getConfig().get("port")).intValue();
        long start = System.nanoTime();
        try {
            InetAddress[] addresses = ssrfGuard.resolvePublic(host);
            IOException last = null;
            // One timeout budget for the whole run, shared across the addresses tried.
            long deadline = start + check.getTimeoutMs() * 1_000_000L;
            for (int i = 0; i < Math.min(addresses.length, MAX_ADDRESSES); i++) {
                InetAddress address = addresses[i];
                int remainingMs = (int) ((deadline - System.nanoTime()) / 1_000_000);
                if (remainingMs <= 0) {
                    last = new SocketTimeoutException();
                    break;
                }
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(address, port), remainingMs);
                    String family = address instanceof Inet6Address ? "IPv6" : "IPv4";
                    return ProbeResult.ok(elapsedMs(start),
                            "Connected over " + family + " to " + address.getHostAddress());
                } catch (IOException e) {
                    last = e;
                }
            }
            return ProbeResult.fail(elapsedMs(start), describe(last, port, check.getTimeoutMs()));
        } catch (HostNotFoundException e) {
            return ProbeResult.fail(elapsedMs(start), "Host not found: the DNS lookup failed");
        } catch (SsrfException e) {
            return ProbeResult.fail(elapsedMs(start), "Target not allowed: " + e.getMessage());
        }
    }

    /**
     * Refused and timed out mean different things: a refusal arrives almost instantly because the
     * machine answered "nothing listens here", while a timeout means no answer at all.
     */
    static String describe(IOException e, int port, int timeoutMs) {
        if (e instanceof SocketTimeoutException) {
            return "Timed out after " + timeoutMs + " ms: the host is unreachable or a firewall is dropping packets";
        }
        if (e instanceof ConnectException && e.getMessage() != null
                && e.getMessage().toLowerCase().contains("refused")) {
            return "Connection refused: the host is up but nothing is listening on port " + port;
        }
        return "Could not connect: " + (e == null ? "unknown error" : e.getMessage());
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
