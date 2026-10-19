package in.sentra.probe;

import in.sentra.check.Check;
import in.sentra.security.SsrfGuard;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TcpProbeTest {

    // Loopback is where the test servers live, so use the guard in its dev (allow-private) mode.
    private final TcpProbe probe = new TcpProbe(new SsrfGuard(true));
    private final TcpProbe strictProbe = new TcpProbe(new SsrfGuard(false));

    private static Check tcpCheck(String host, int port) {
        Check c = new Check();
        c.setType(CheckType.TCP);
        c.setConfig(Map.of("host", host, "port", port));
        c.setTimeoutMs(2000);
        return c;
    }

    @Test
    void openPortConnects() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            ProbeResult r = probe.execute(tcpCheck("127.0.0.1", server.getLocalPort()));
            assertThat(r.success()).isTrue();
            assertThat(r.detail()).contains("IPv4").contains("127.0.0.1");
        }
    }

    @Test
    void closedPortIsReportedAsRefusedNotAsATimeout() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
        } // closed again: nothing listens here now
        ProbeResult r = probe.execute(tcpCheck("127.0.0.1", port));
        assertThat(r.success()).isFalse();
        assertThat(r.error()).startsWith("Connection refused").contains(String.valueOf(port));
    }

    @Test
    void unknownHostIsReportedAsADnsFailure() {
        ProbeResult r = probe.execute(tcpCheck("this-host-does-not-exist.invalid", 80));
        assertThat(r.success()).isFalse();
        assertThat(r.error()).startsWith("Host not found");
    }

    @Test
    void strictGuardRefusesInternalTargetsWithoutConnecting() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            ProbeResult r = strictProbe.execute(tcpCheck("127.0.0.1", server.getLocalPort()));
            assertThat(r.success()).isFalse();
            assertThat(r.error()).startsWith("Target not allowed");
        }
    }
}
