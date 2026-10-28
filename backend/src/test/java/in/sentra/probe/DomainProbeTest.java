package in.sentra.probe;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DomainProbeTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) {
        return mapper.readTree(s);
    }

    private static final String RDAP = """
            {
              "ldhName": "EXAMPLE.COM",
              "events": [
                { "eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z" },
                { "eventAction": "expiration",   "eventDate": "2027-08-13T04:00:00Z" },
                { "eventAction": "last update of RDAP database", "eventDate": "2026-10-28T10:00:00Z" }
              ],
              "entities": [
                { "roles": ["registrar"],
                  "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "RESERVED-Internet Assigned Numbers Authority"]]] }
              ]
            }
            """;

    @Test
    void readsTheExpirationEventAndTheRegistrar() {
        JsonNode body = json(RDAP);
        assertThat(DomainProbe.expiry(body)).isEqualTo(Instant.parse("2027-08-13T04:00:00Z"));
        assertThat(DomainProbe.registrar(body)).isEqualTo("RESERVED-Internet Assigned Numbers Authority");
    }

    @Test
    void noExpirationEventMeansNoDate() {
        assertThat(DomainProbe.expiry(json("{\"events\":[{\"eventAction\":\"registration\",\"eventDate\":\"2020-01-01T00:00:00Z\"}]}")))
                .isNull();
        assertThat(DomainProbe.expiry(json("{}"))).isNull();
    }

    @Test
    void datesWithoutAnOffsetAreReadAsUtcAndGarbageIsNull() {
        assertThat(DomainProbe.parseDate("2027-01-02T03:04:05")).isEqualTo(Instant.parse("2027-01-02T03:04:05Z"));
        assertThat(DomainProbe.parseDate("2027-01-02T03:04:05.123+05:30")).isEqualTo(Instant.parse("2027-01-01T21:34:05.123Z"));
        assertThat(DomainProbe.parseDate("next tuesday")).isNull();
    }

    @Test
    void farFromExpiryIsHealthy() {
        ProbeResult r = DomainProbe.assess(NOW.plus(java.time.Duration.ofDays(200)), NOW, 30, "Example Registrar");
        assertThat(r.success()).isTrue();
        assertThat(r.detail()).contains("200 days left").contains("Example Registrar");
    }

    @Test
    void insideTheWarningWindowFails() {
        ProbeResult r = DomainProbe.assess(NOW.plus(java.time.Duration.ofDays(10)), NOW, 30, null);
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("expires in 10 day(s)").contains("30-day warning window");
    }

    @Test
    void lapsedRegistrationFails() {
        ProbeResult r = DomainProbe.assess(NOW.minus(java.time.Duration.ofDays(3)), NOW, 30, null);
        assertThat(r.success()).isFalse();
        assertThat(r.error()).contains("expired 3 day(s) ago");
    }
}
