package org.limelightcreatives.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleMailer implements LoginCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMailer.class);

    @Override
    public void send(String email, String code) {
        log.info("[dev] login code for {}: {}", email, code);
    }
}
