package in.sentra.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the local docker-compose Postgres (no Testcontainers, per PLAN.md). The scheduler tick
 * is slowed so the test checks are never probed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=18080", "sentra.scheduler.tick-ms=3600000",
                "sentra.jwt.secret=test-only-signing-key-0123456789abcdef"})
class TenantIsolationTest {

    private static final String BASE = "http://localhost:18080/api/v1";

    static {
        // SentraApplication.main does this in production; tests bypass main (see docs/decisions.md).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    AppUserRepository users;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // Deleting the user cascades to project -> checks -> runs/incidents/rules.
        emails.forEach(e -> users.findByEmail(e).ifPresent(users::delete));
    }

    @Test
    void anotherUsersCheckIsInvisible() throws Exception {
        String a = register();
        String b = register();

        HttpResponse<String> created = send("POST", "/checks", a,
                "{\"name\":\"mine\",\"target\":\"http://93.184.216.34\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(created.body());
        assertThat(m.find()).isTrue();
        String id = m.group(1);

        assertThat(send("GET", "/checks/" + id, a, null).statusCode()).isEqualTo(200);

        // Cross-tenant: 404 (not 403) on every route, so existence isn't confirmed.
        assertThat(send("GET", "/checks/" + id, b, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/checks/" + id + "/run", b, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/checks/" + id + "/runs", b, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/checks/" + id + "/incidents", b, null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/checks/" + id + "/alert-rules", b, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/checks/" + id + "/alert-rules", b,
                "{\"url\":\"http://93.184.216.34/hook\"}").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/checks", b, null).body()).isEqualTo("[]");
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        assertThat(send("GET", "/checks", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/checks", "not-a-real-token", null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/ping", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void ssrfTargetsAreRejectedAtCreation() throws Exception {
        String a = register();
        for (String target : List.of("http://localhost:8080", "http://169.254.169.254/latest/meta-data",
                "http://10.0.0.1")) {
            HttpResponse<String> r = send("POST", "/checks", a, "{\"name\":\"x\",\"target\":\"" + target + "\"}");
            assertThat(r.statusCode()).as(target).isEqualTo(400);
        }
        assertThat(send("GET", "/checks", a, null).body()).isEqualTo("[]");
    }

    private String register() throws Exception {
        String email = "tenant-test-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        HttpResponse<String> r = send("POST", "/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}");
        assertThat(r.statusCode()).isEqualTo(201);
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(r.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
