package org.limelightcreatives.auth.login;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class LoginCodesTest extends DatabaseTest {

    @Autowired
    LoginCodes codes;

    private String newUser() {
        this.db.sql("INSERT INTO \"users\" (\"id\", \"email\") VALUES ('u1', 'u1@example.com')").update();
        return "u1";
    }

    private long successes(List<Callable<Boolean>> calls) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            long ok = 0;
            for (Future<Boolean> f : pool.invokeAll(calls)) if (f.get()) ok++;
            return ok;
        }
    }

    @Test
    void codesAreSixDigitsAndStoredHashed() {
        String user = newUser();
        String code = this.codes.issue(user);
        assertThat(code).matches("\\d{6}");
        String stored = this.db.sql("SELECT \"codeHash\" FROM \"verification_codes\"").query(String.class).single();
        assertThat(stored).startsWith("$2a$10$").doesNotContain(code);
    }

    @Test
    void manyGuessesAtOnceStillOnlyGetFiveTries() throws Exception {
        String user = newUser();
        String code = this.codes.issue(user);
        String wrong = code.equals("000000") ? "111111" : "000000";

        List<Callable<Boolean>> guesses = new ArrayList<>();
        for (int i = 0; i < 20; i++) guesses.add(() -> this.codes.verify(user, wrong));
        successes(guesses);

        assertThat(this.db.sql("SELECT \"attempts\" FROM \"verification_codes\"").query(Integer.class).single()).isEqualTo(5);
        assertThat(this.codes.verify(user, code)).isFalse();
    }

    @Test
    void theRightCodeSentTwiceAtOnceSignsInOnce() throws Exception {
        String user = newUser();
        String code = this.codes.issue(user);
        assertThat(successes(List.of(() -> this.codes.verify(user, code), () -> this.codes.verify(user, code)))).isEqualTo(1);
    }

    @Test
    void expiredAndUsedCodesDontWork() {
        String user = newUser();
        String code = this.codes.issue(user);
        assertThat(this.codes.verify(user, code)).isTrue();
        assertThat(this.codes.verify(user, code)).as("used").isFalse();

        String late = this.codes.issue(user);
        this.db.sql("UPDATE \"verification_codes\" SET \"expiresAt\" = \"createdAt\" - interval '1 second'").update();
        assertThat(this.codes.verify(user, late)).as("expired").isFalse();
    }

    @Test
    void onlyTheLatestCodeWorks() {
        String user = newUser();
        String first = this.codes.issue(user);
        String second = this.codes.issue(user);
        if (!first.equals(second)) assertThat(this.codes.verify(user, first)).isFalse();
        assertThat(this.codes.verify(user, second)).isTrue();
    }

    @Test
    void codesFromTheOldAppStillWork() {
        String user = newUser();
        String hash = new BCryptPasswordEncoder(10).encode("123456")
                .replaceFirst("^\\$2a\\$", "\\$2b\\$");
        this.db.sql("""
                INSERT INTO "verification_codes" ("id", "userId", "codeHash", "expiresAt")
                VALUES ('c1', 'u1', ?, (now() at the timezone 'utc') + interval '10 minutes')
                """).param(hash).update();
        assertThat(this.codes.verify(user, "123456")).isTrue();
    }
}
