package org.limelightcreatives.auth.mail;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Sends codes through Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email). */
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
        resend.post().uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", from,
                        "to", List.of(email),
                        "subject", SUBJECT,
                        "text", LoginCodeMailer.body(email, code)))
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> {
                    // Resend explains what's wrong in the body, e.g. an unverified "from" domain.
                    String reason = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    throw new IllegalStateException("Resend refused the email (" + response.getStatusCode() + "): " + reason);
                })
                .toBodilessEntity();
    }
}
