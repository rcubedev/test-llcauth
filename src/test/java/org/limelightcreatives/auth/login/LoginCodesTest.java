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
    void sendingMoreCodesDoesntBuyMoreGuesses() {
        String user = newUser();
        for (int i = 0; i < 5; i++) {
            String code = this.codes.issue(user);
            assertThat(this.codes.verify(user, code.equals("000000") ? "111111" : "000000")).isFalse();
        }
        assertThat(this.codes.verify(user, this.codes.issue(user))).as("five wrong guesses used, whatever was sent since").isFalse();

        this.db.sql("UPDATE \"verification_codes\" SET \"createdAt\" = \"createdAt\" - interval '16 minutes'").update();
        assertThat(this.codes.verify(user, this.codes.issue(user))).isTrue();
    }

    @Test
    void onlyTheNewestThreeCodesWork() {
        String user = newUser();
        String oldest = this.codes.issue(user);
        String second = this.codes.issue(user);
        String third = this.codes.issue(user);
        String newest = this.codes.issue(user);

        if (!List.of(second, third, newest).contains(oldest)) assertThat(this.codes.verify(user, oldest)).isFalse();
        assertThat(this.codes.verify(user, second)).isTrue();
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
    void anEarlierCodeStillWorksAfterAnotherIsSent() {
        String user = newUser();
        String first = this.codes.issue(user);
        String second = this.codes.issue(user);

        assertThat(this.codes.verify(user, first)).isTrue();
        assertThat(this.codes.verify(user, second)).as("signing in uses up the rest").isFalse();
    }

    @Test
    void anExpiredEarlierCodeDoesnt() {
        String user = newUser();
        String first = this.codes.issue(user);
        this.db.sql("UPDATE \"verification_codes\" SET \"expiresAt\" = \"createdAt\" - interval '1 second'").update();
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
