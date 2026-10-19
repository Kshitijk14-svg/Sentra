package in.sentra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SentraApplication {
    public static void main(String[] args) {
        // All timestamps are stored and reasoned about in UTC (Doc 1 §5.1: never trust a
        // client clock). Also works around the JVM reporting a legacy zone id (e.g.
        // "Asia/Calcutta") that recent PostgreSQL builds reject as an unknown TimeZone GUC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SentraApplication.class, args);
    }
}
