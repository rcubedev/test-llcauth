package org.limelightcreatives.auth.mail;

import org.limelightcreatives.auth.AuthProperties;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class MailConfig {

    @Bean
    LoginCodeMailer loginCodeMailer(AuthProperties properties) {
        String apiKey = properties.mail().resendApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            LoggerFactory.getLogger(MailConfig.class)
                    .warn("No RESEND_API_KEY: login codes will be written to the log, not emailed.");
            return new ConsoleMailer();
        }
        return new ResendMailer(RestClient.builder(), apiKey, properties.mail().from());
    }
}
