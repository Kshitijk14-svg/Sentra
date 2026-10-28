package in.sentra.probe;

import in.sentra.check.Check;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Domain registration expiry over RDAP (Doc 1 §5.4): JSON over HTTPS, read the "expiration" event.
 * rdap.org redirects to the registry that is authoritative for the TLD. The query goes to that
 * registry, never to the user's domain, so there is no user-controlled connection to SSRF-guard.
 * Registries differ and some publish no expiry date, so an unclear answer is inconclusive: it is
 * recorded but neither fails nor passes the check. "Could not tell" must never read as "expiring".
 */
@Component
public class DomainProbe implements Probe {

    public static final int DEFAULT_WARN_DAYS = 30;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // rdap.org bootstraps to the registry by redirect
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String baseUrl;

    public DomainProbe(ObjectMapper mapper, Clock clock,
                       @Value("${sentra.rdap.base-url:https://rdap.org}") String baseUrl) {
        this.mapper = mapper;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Override
    public CheckType supports() {
        return CheckType.DOMAIN;
    }

    @Override
    public ProbeResult execute(Check check) {
        String domain = String.valueOf(check.getConfig().get("domain"));
        int warnDays = check.getConfig().get("warnDays") instanceof Number n ? n.intValue() : DEFAULT_WARN_DAYS;
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/domain/" + domain))
                    .timeout(Duration.ofMillis(check.getTimeoutMs()))
                    .header("Accept", "application/rdap+json, application/json")
                    .header("User-Agent", "Sentra-Monitor/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = elapsedMs(start);
            int status = response.statusCode();
            if (status == 404) {
                // A 404 from the bootstrap service itself means it knows no registry for this TLD;
                // a 404 from the registry means the domain is not registered there.
                boolean fromBootstrap = response.uri().getHost().equalsIgnoreCase(URI.create(baseUrl).getHost());
                return fromBootstrap
                        ? ProbeResult.inconclusive(latency, "No RDAP registry is known for ." + tld(domain))
                        : new ProbeResult(false, status, latency,
                                "Domain is not registered: the registry has no record of " + domain, null, false);
            }
            if (status != 200) {
                return new ProbeResult(false, status, latency, "Registry answered HTTP " + status, null, true);
            }
            JsonNode body = mapper.readTree(response.body());
            Instant expiry = expiry(body);
            if (expiry == null) {
                return ProbeResult.inconclusive(latency, "The registry does not publish an expiry date for " + domain);
            }
            ProbeResult verdict = assess(expiry, clock.instant(), warnDays, registrar(body));
            return new ProbeResult(verdict.success(), status, latency, verdict.error(), verdict.detail(), false);
        } catch (HttpTimeoutException e) {
            return ProbeResult.inconclusive(elapsedMs(start), "The RDAP registry did not answer within "
                    + check.getTimeoutMs() + " ms");
        } catch (IOException e) {
            return ProbeResult.inconclusive(elapsedMs(start), "Could not reach the RDAP registry: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeResult.inconclusive(elapsedMs(start), "Interrupted");
        } catch (RuntimeException e) {
            // Malformed JSON and other surprises in the answer: we could not tell, so do not alert.
            return ProbeResult.inconclusive(elapsedMs(start), "Could not read the registry's answer: " + e.getMessage());
        }
    }

    /** The date of the "expiration" event, or null when the registry publishes none. */
    static Instant expiry(JsonNode rdap) {
        for (JsonNode event : rdap.path("events")) {
            if ("expiration".equalsIgnoreCase(event.path("eventAction").asString(""))) {
                return parseDate(event.path("eventDate").asString(""));
            }
        }
        return null;
    }

    /** The registrar's display name from its vCard, or null. */
    static String registrar(JsonNode rdap) {
        for (JsonNode entity : rdap.path("entities")) {
            boolean isRegistrar = false;
            for (JsonNode role : entity.path("roles")) {
                isRegistrar |= "registrar".equalsIgnoreCase(role.asString(""));
            }
            if (!isRegistrar) {
                continue;
            }
            for (JsonNode property : entity.path("vcardArray").path(1)) {
                if ("fn".equals(property.path(0).asString("")) && property.path(3).isString()) {
                    return property.path(3).asString();
                }
            }
        }
        return null;
    }

    /** RDAP dates are RFC 3339, but some registries drop the offset; read those as UTC. */
    static Instant parseDate(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    /** Pure expiry rule, the same shape as the certificate check: healthy, inside the warning window, or lapsed. */
    static ProbeResult assess(Instant expiry, Instant now, int warnDays, String registrar) {
        long daysLeft = Duration.between(now, expiry).toDays();
        String when = expiry.toString().substring(0, 10);
        if (expiry.isBefore(now)) {
            return ProbeResult.fail(0, "Domain registration expired " + Math.max(1, -daysLeft) + " day(s) ago (" + when + ")");
        }
        if (daysLeft < warnDays) {
            return ProbeResult.fail(0, "Domain expires in " + daysLeft + " day(s) (" + when
                    + "), inside the " + warnDays + "-day warning window");
        }
        return ProbeResult.ok(0, "Registered until " + when + " (" + daysLeft + " days left)"
                + (registrar != null ? ", registrar " + registrar : ""));
    }

    private static String tld(String domain) {
        return domain.substring(domain.lastIndexOf('.') + 1);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
