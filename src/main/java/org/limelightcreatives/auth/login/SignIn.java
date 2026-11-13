package org.limelightcreatives.auth.login;

import org.limelightcreatives.auth.mail.LoginCodeMailer;
import org.limelightcreatives.auth.mail.MailProperties;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Optional;

@Service
public class SignIn {

    private static final Logger log = LoggerFactory.getLogger(SignIn.class);

    static final Duration WINDOW = Duration.ofHours(1);
    static final Duration EMAIL_WINDOW = Duration.ofMinutes(15);

    public enum SendResult {
        SENT("Code sent."),
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
    private final SignInLimits limits;
    private final TransactionTemplate transaction;
    private final JdbcClient db;

    public SignIn(Users users, LoginCodes codes, RateLimits rateLimits, LoginCodeMailer mailer, MailProperties mail,
                  SignInLimits limits, TransactionTemplate transaction, JdbcClient db) {
        this.users = users;
        this.codes = codes;
        this.rateLimits = rateLimits;
        this.mailer = mailer;
        this.logCodeIfUnsent = mail.logCodeIfUnsent();
        this.limits = limits;
        this.transaction = transaction;
        this.db = db;
    }

    private record Issued(SendResult refused, String code) {}

    public SendResult sendCode(String email, String ip) {
        String normalised = Users.normaliseEmail(email);
        if (normalised.length() > 254 || !normalised.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            return SendResult.INVALID_EMAIL;

        Issued issued = this.transaction.execute(status -> {
            lock("code-email:" + normalised);
            lock("code-ip:" + ip);

            Optional<User> existing = this.users.findByEmail(normalised);
            if (existing.isPresent() && this.codes.issuedSince(existing.get().id(), EMAIL_WINDOW) >= this.limits.codesPerEmail()) {
                return new Issued(SendResult.TOO_MANY_FOR_EMAIL, null);
            }
            if (this.rateLimits.count(ip, RateLimits.SEND_CODE, WINDOW) >= this.limits.codesPerIp()) {
                return new Issued(SendResult.TOO_MANY_FOR_IP, null);
            }

            User user = existing.orElseGet(() -> this.users.findOrCreate(normalised));
            this.rateLimits.record(ip, RateLimits.SEND_CODE);
            return new Issued(null, this.codes.issue(user.id()));
        });
        if (issued.refused() != null) return issued.refused();
        String code = issued.code();

        try {
            this.mailer.send(normalised, code);
            return SendResult.SENT;
        } catch (RuntimeException e) {
            log.error("Couldn't email a login code to {}", normalised, e);
            if (this.logCodeIfUnsent) {
                log.warn("[dev] The email wasn't sent so here is the login code for {}: {}", normalised, code);
                return SendResult.LOGGED_INSTEAD;
            }
            return SendResult.NOT_DELIVERED;
        }
    }

    private void lock(String key) {
        db.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").param(key).query((_, _) -> 0).single();
    }

    public Optional<User> verify(String email, String code) {
        return this.users.findByEmail(email).filter(user -> this.codes.verify(user.id(), code));
    }
}
