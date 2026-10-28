package in.sentra.probe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CronProbeTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void pingInsidePeriodIsHealthy() {
        ProbeResult r = CronProbe.assess(NOW.minusSeconds(3000), "SUCCESS", NOW.minusSeconds(99999), NOW, 3600, 300);
        assertThat(r.success()).isTrue();
    }

    @Test
    void pingInsideGraceIsStillHealthy() {
        ProbeResult r = CronProbe.assess(NOW.minusSeconds(3800), "SUCCESS", NOW.minusSeconds(99999), NOW, 3600, 300);
        assertThat(r.success()).isTrue();
    }

    @Test
    void latePingFails() {
        ProbeResult r = CronProbe.assess(NOW.minusSeconds(4000), "SUCCESS", NOW.minusSeconds(99999), NOW, 3600, 300);
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("No ping for");
    }

    @Test
    void failurePingFailsEvenWhenRecent() {
        ProbeResult r = CronProbe.assess(NOW.minusSeconds(10), "FAIL", NOW.minusSeconds(99999), NOW, 3600, 300);
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("failure");
    }

    @Test
    void newCheckWaitsForItsFirstPingThenFails() {
        assertThat(CronProbe.assess(null, null, NOW.minusSeconds(100), NOW, 3600, 300).success()).isTrue();
        assertThat(CronProbe.assess(null, null, NOW.minusSeconds(5000), NOW, 3600, 300).success()).isFalse();
    }
}
