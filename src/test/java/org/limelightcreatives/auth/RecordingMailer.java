package org.limelightcreatives.auth;

import org.limelightcreatives.auth.mail.LoginCodeMailer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecordingMailer implements LoginCodeMailer {

    private final Map<String, String> lastCode = new ConcurrentHashMap<>();

    @Override
    public void send(String email, String code) {
        this.lastCode.put(email, code);
    }

    public String lastCodeFor(String email) {
        String code = this.lastCode.get(email);
        if (code == null) throw new AssertionError("No code was sent to " + email);
        return code;
    }

    public boolean sentTo(String email) {
        return this.lastCode.containsKey(email);
    }

    public void clear() {
        this.lastCode.clear();
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
