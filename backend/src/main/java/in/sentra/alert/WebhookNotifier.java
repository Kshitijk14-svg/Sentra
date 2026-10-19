package in.sentra.alert;

import in.sentra.security.SsrfGuard;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class WebhookNotifier implements Notifier {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper mapper;
    private final SsrfGuard ssrfGuard;

    public WebhookNotifier(ObjectMapper mapper, SsrfGuard ssrfGuard) {
        this.mapper = mapper;
        this.ssrfGuard = ssrfGuard;
    }

    @Override
    public String channel() {
        return "WEBHOOK";
    }

    @Override
    public void send(String destination, AlertMessage message) throws IOException, InterruptedException {
        ssrfGuard.validate(destination); // re-check at send time; DNS can change after the rule was created
        HttpRequest request = HttpRequest.newBuilder(URI.create(destination))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(message)))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Webhook returned HTTP " + response.statusCode());
        }
    }
}
