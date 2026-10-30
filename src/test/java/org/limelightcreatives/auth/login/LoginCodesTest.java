package org.limelightcreatives.auth.login;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The code rules hold even when many requests arrive at once. */
class LoginCodesTest extends DatabaseTest {

    @Autowired LoginCodes codes;

    private String newUser() {
        db.sql("INSERT INTO \"users\" (\"id\", \"email\") VALUES ('u1', 'u1@example.com')").update();
        return "u1";
    }

    private long successes(List<Callable<Boolean>> calls) throws Exception {
        try (var pool = Executors.newFixedThreadPool(calls.size())) {
            long ok = 0;
            for (Future<Boolean> f : pool.invokeAll(calls)) if (f.get()) ok++;
            return ok;
        }
    }

    @Test
    void codesAreSixDigitsAndStoredHashed() {
        String user = newUser();
        String code = codes.issue(user);
        assertThat(code).matches("\\d{6}");
        String stored = db.sql("SELECT \"codeHash\" FROM \"verification_codes\"").query(String.class).single();
        assertThat(stored).startsWith("$2a$10$").doesNotContain(code);
    }

    @Test
    void manyGuessesAtOnceStillOnlyGetFiveTries() throws Exception {
        String user = newUser();
        String code = codes.issue(user);
        String wrong = code.equals("000000") ? "111111" : "000000";

        List<Callable<Boolean>> guesses = new ArrayList<>();
        for (int i = 0; i < 20; i++) guesses.add(() -> codes.verify(user, wrong));
        successes(guesses);

        assertThat(db.sql("SELECT \"attempts\" FROM \"verification_codes\"").query(Integer.class).single()).isEqualTo(5);
        assertThat(codes.verify(user, code)).isFalse();
    }

    @Test
    void theRightCodeSentTwiceAtOnceSignsInOnce() throws Exception {
        String user = newUser();
        String code = codes.issue(user);
        assertThat(successes(List.of(() -> codes.verify(user, code), () -> codes.verify(user, code)))).isEqualTo(1);
    }

    @Test
    void codesFromTheOldAppStillWork() {
        // bcryptjs (the old app) wrote $2a$/$2b$ hashes; a code someone was emailed just before the switch still works.
        String user = newUser();
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode("123456")
                .replaceFirst("^\\$2a\\$", "\\$2b\\$");
        db.sql("""
                INSERT INTO "verification_codes" ("id", "userId", "codeHash", "expiresAt")
                VALUES ('c1', 'u1', ?, (now() at time zone 'utc') + interval '10 minutes')
                """).param(hash).update();
        assertThat(codes.verify(user, "123456")).isTrue();
    }
}
