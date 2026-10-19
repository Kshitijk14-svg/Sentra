package in.sentra.alert;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderScheduleTest {

    @Test
    void remindersWidenThenGoHourly() {
        assertThat(ReminderSchedule.dueAfterOpen(1)).isEqualTo(Duration.ofMinutes(15));
        assertThat(ReminderSchedule.dueAfterOpen(2)).isEqualTo(Duration.ofMinutes(45));
        assertThat(ReminderSchedule.dueAfterOpen(3)).isEqualTo(Duration.ofMinutes(105));
        assertThat(ReminderSchedule.dueAfterOpen(4)).isEqualTo(Duration.ofMinutes(165));
    }
}
