package org.limelightcreatives.auth.mail;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MailConfigTest {

    private final MailConfig config = new MailConfig();

    @Test
    void productionWontStartWithoutAKey() {
        assertThatThrownBy(() -> this.config.loginCodeMailer(new MailProperties("", "from@example.com",
                false, false), RestClient.builder()))
                .hasMessageContaining("(RESEND_API_KEY) must be set");
    }

    @Test
    void developmentLogsCodesInstead() {
        assertThat(this.config.loginCodeMailer(new MailProperties(null, "from@example.com",
                true, true), RestClient.builder()))
                .isInstanceOf(ConsoleMailer.class);
    }

    @Test
    void withAKeyCodesAreEmailed() {
        assertThat(this.config.loginCodeMailer(new MailProperties("re_123", "from@example.com",
                false, false), RestClient.builder()))
                .isInstanceOf(ResendMailer.class);
    }
}
