package org.limelightcreatives.auth.mail;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class MailConfig {

    @Bean
    LoginCodeMailer loginCodeMailer(MailProperties mail, RestClient.Builder restClient) {
        if (mail.resendApiKey() == null || mail.resendApiKey().isBlank()) {
            LoggerFactory.getLogger(MailConfig.class)
                    .warn("No RESEND_API_KEY. Login codes will be written to the log instead of being emailed.");
            return new ConsoleMailer();
        }
        return new ResendMailer(restClient, mail.resendApiKey(), mail.from());
    }
}
