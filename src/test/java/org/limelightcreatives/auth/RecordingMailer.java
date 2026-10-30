package org.limelightcreatives.auth;

import org.limelightcreatives.auth.mail.LoginCodeMailer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stands in for email in tests: remembers the last code sent to each address. */
public class RecordingMailer implements LoginCodeMailer {

    private final Map<String, String> lastCode = new ConcurrentHashMap<>();

    @Override
    public void send(String email, String code) {
        lastCode.put(email, code);
    }

    public String lastCodeFor(String email) {
        String code = lastCode.get(email);
        if (code == null) throw new AssertionError("No code was sent to " + email);
        return code;
    }

    public boolean sentTo(String email) {
        return lastCode.containsKey(email);
    }

    public void clear() {
        lastCode.clear();
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }
}
