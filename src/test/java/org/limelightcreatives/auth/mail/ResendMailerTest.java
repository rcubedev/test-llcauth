package org.limelightcreatives.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendMailerTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer resend = MockRestServiceServer.bindTo(this.builder).build();
    private final ResendMailer mailer = new ResendMailer(builder, "re_test_key", "Limelight <sign-in@auth.limelightcreatives.org>");

    @Test
    void sendsTheCodeThroughResendsApi() {
        this.resend.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(jsonPath("$.from").value("Limelight <sign-in@auth.limelightcreatives.org>"))
                .andExpect(jsonPath("$.to[0]").value("sam@example.com"))
                .andExpect(jsonPath("$.subject").value("Your Limelight Creatives sign-in code"))
                .andExpect(jsonPath("$.text").value(containsString("Your sign-in code is 123456.")))
                .andRespond(withSuccess("{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}", MediaType.APPLICATION_JSON));

        this.mailer.send("sam@example.com", "123456");
        this.resend.verify();
    }

    @Test
    void resendsReasonIsKeptWhenItRefuses() {
        this.resend.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":403,\"message\":\"The auth.limelightcreatives.org domain is not verified.\"}"));

        assertThatThrownBy(() -> this.mailer.send("sam@example.com", "123456"))
                .hasMessageContaining("403")
                .hasMessageContaining("domain is not verified");
    }
}
