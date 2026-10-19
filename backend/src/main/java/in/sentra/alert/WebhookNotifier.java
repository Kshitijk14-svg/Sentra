package in.sentra.alert;

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

    public WebhookNotifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String channel() {
        return "WEBHOOK";
    }

    @Override
    public void send(String destination, AlertMessage message) throws IOException, InterruptedException {
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
