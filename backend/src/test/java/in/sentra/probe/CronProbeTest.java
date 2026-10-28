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
    void defaultGraceIsTwentyPercentOfThePeriodBetweenTenSecondsAndAnHour() {
        assertThat(CronProbe.defaultGrace(30)).isEqualTo(10);      // floor
        assertThat(CronProbe.defaultGrace(60)).isEqualTo(12);
        assertThat(CronProbe.defaultGrace(900)).isEqualTo(180);
        assertThat(CronProbe.defaultGrace(3600)).isEqualTo(720);
        assertThat(CronProbe.defaultGrace(86400)).isEqualTo(3600); // ceiling
    }

    @Test
    void thirtySecondJobIsEvaluatedEveryFortySecondsAtMostWhenSilent() {
        Instant lastPing = NOW.minusSeconds(5);
        assertThat(CronProbe.nextCheck(lastPing, null, NOW, 30, CronProbe.defaultGrace(30)))
                .isEqualTo(lastPing.plusSeconds(30 + 10 + 1));
    }

    @Test
    void nextEvaluationIsTheDeadlineNotAFixedTimer() {
        // Daily job, pinged an hour ago, 10 min grace: next look is 23 h + 10 min + 1 s from now.
        Instant next = CronProbe.nextCheck(NOW.minusSeconds(3600), NOW.minusSeconds(999999), NOW, 86400, 600);
        assertThat(next).isEqualTo(NOW.plusSeconds(86400 + 600 + 1 - 3600));
    }

    @Test
    void newCheckIsFirstEvaluatedAtItsFirstDeadline() {
        Instant created = NOW.minusSeconds(10);
        assertThat(CronProbe.nextCheck(null, created, NOW, 3600, 300)).isEqualTo(created.plusSeconds(3901));
    }

    @Test
    void alreadyLateJobIsReEvaluatedOncePerPeriod() {
        Instant next = CronProbe.nextCheck(NOW.minusSeconds(200000), NOW.minusSeconds(999999), NOW, 86400, 600);
        assertThat(next).isEqualTo(NOW.plusSeconds(86400));
        // ...but never more often than once a minute, even for a very short period.
        assertThat(CronProbe.nextCheck(NOW.minusSeconds(500), null, NOW, 10, 0)).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void newCheckWaitsForItsFirstPingThenFails() {
        assertThat(CronProbe.assess(null, null, NOW.minusSeconds(100), NOW, 3600, 300).success()).isTrue();
        assertThat(CronProbe.assess(null, null, NOW.minusSeconds(5000), NOW, 3600, 300).success()).isFalse();
    }
}
