package org.limelightcreatives.auth.login;

import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.mail.LoginCodeMailer;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/** Email-code sign in: send someone a code, then check it. */
@Service
public class SignIn {

    private static final Logger log = LoggerFactory.getLogger(SignIn.class);

    static final Duration WINDOW = Duration.ofHours(1);
    static final int MAX_CODES_PER_EMAIL = 5;
    // Covers one person trying many different emails.
    static final int MAX_CODES_PER_IP = 15;

    public enum SendResult {
        SENT("Code sent."),
        // Local testing only (auth.mail.log-code-if-unsent): the email failed, but the code is in the log.
        LOGGED_INSTEAD("Couldn't email the code (see the server log for why), so it's printed in the log instead. Local testing only."),
        INVALID_EMAIL("Enter a valid email address."),
        TOO_MANY_FOR_EMAIL("Too many codes requested. Please try again later."),
        TOO_MANY_FOR_IP("Too many requests. Please try again later."),
        NOT_DELIVERED("Something went wrong. Try again.");

        public final String message;

        SendResult(String message) {
            this.message = message;
        }
    }

    private final Users users;
    private final LoginCodes codes;
    private final RateLimits rateLimits;
    private final LoginCodeMailer mailer;
    private final boolean logCodeIfUnsent;

    public SignIn(Users users, LoginCodes codes, RateLimits rateLimits, LoginCodeMailer mailer, AuthProperties properties) {
        this.users = users;
        this.codes = codes;
        this.rateLimits = rateLimits;
        this.mailer = mailer;
        this.logCodeIfUnsent = properties.mail().logCodeIfUnsent();
    }

    /** Emails a new code, creating the account if this email hasn't signed in before. */
    public SendResult sendCode(String email, String ip) {
        String normalized = Users.normalizeEmail(email);
        if (normalized.length() > 254 || !normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return SendResult.INVALID_EMAIL;
        }

        Optional<User> existing = users.findByEmail(normalized);
        if (existing.isPresent() && codes.issuedSince(existing.get().id(), WINDOW) >= MAX_CODES_PER_EMAIL) {
            return SendResult.TOO_MANY_FOR_EMAIL;
        }
        if (rateLimits.count(ip, RateLimits.SEND_CODE, WINDOW) >= MAX_CODES_PER_IP) {
            return SendResult.TOO_MANY_FOR_IP;
        }

        User user = existing.orElseGet(() -> users.findOrCreate(normalized));
        String code = codes.issue(user.id());
        rateLimits.record(ip, RateLimits.SEND_CODE);

        try {
            mailer.send(normalized, code);
            return SendResult.SENT;
        } catch (RuntimeException e) {
            log.error("Couldn't email a login code to {}", normalized, e);
            if (logCodeIfUnsent) {
                log.warn("[dev] The email wasn't sent, so here is the login code for {}: {}", normalized, code);
                return SendResult.LOGGED_INSTEAD;
            }
            return SendResult.NOT_DELIVERED;
        }
    }

    /** The user, if this is their current code. */
    public Optional<User> verify(String email, String code) {
        return users.findByEmail(email).filter(user -> codes.verify(user.id(), code));
    }
}
