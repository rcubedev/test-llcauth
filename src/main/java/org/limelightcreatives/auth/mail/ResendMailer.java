package org.limelightcreatives.auth.mail;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ResendMailer implements LoginCodeMailer {

    private final RestClient resend;
    private final String from;

    public ResendMailer(RestClient.Builder builder, String apiKey, String from) {
        this.resend = builder.baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    @Override
    public void send(String email, String code) {
        this.resend.post().uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", List.of(email),
                        "subject", SUBJECT,
                        "text", LoginCodeMailer.body(email, code)))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String reason = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new IllegalStateException("Resend refused the email (" + response.getStatusCode() + "): " + reason);
                })
                .toBodilessEntity();
    }
}
