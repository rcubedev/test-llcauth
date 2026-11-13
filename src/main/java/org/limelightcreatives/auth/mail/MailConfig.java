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
            if (!mail.logCodesWithoutKey())
                throw new IllegalStateException("auth.mail.resend-api-key (RESEND_API_KEY) must be set to email login codes");
            LoggerFactory.getLogger(MailConfig.class)
                    .warn("No RESEND_API_KEY. Login codes will be written to the log instead of being emailed.");
            return new ConsoleMailer();
        }
        return new ResendMailer(restClient, mail.resendApiKey(), mail.from());
    }
}
