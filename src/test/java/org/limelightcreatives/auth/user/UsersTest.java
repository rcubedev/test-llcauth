package org.limelightcreatives.auth.user;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class UsersTest extends DatabaseTest {

    @Autowired
    Users users;

    @Test
    void emailsAreStoredLowerCaseAndTrimmed() {
        User user = this.users.findOrCreate("  Sam@Example.COM ");
        assertThat(user.email()).isEqualTo("sam@example.com");
        assertThat(this.users.findByEmail("SAM@example.com")).contains(user);
    }

    @Test
    void newPeopleAreParticipantsWithoutAName() {
        User user = this.users.findOrCreate("sam@example.com");
        assertThat(user.role()).isEqualTo(Role.PARTICIPANT);
        assertThat(user.needsProfile()).isTrue();
        assertThat(user.name()).isNull();

        this.users.updateName(user.id(), "Sam", "Lee");
        User named = this.users.findById(user.id()).orElseThrow();
        assertThat(named.needsProfile()).isFalse();
        assertThat(named.name()).isEqualTo("Sam Lee");
    }

    @Test
    void twoSignUpsForTheSameEmailAtOnceShareOneAccount() throws Exception {
        Callable<User> signUp = () -> this.users.findOrCreate("sam@example.com");
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<String> ids = pool.invokeAll(List.of(signUp, signUp, signUp, signUp)).stream()
                    .map(UsersTest::get).map(User::id).distinct().toList();
            assertThat(ids).hasSize(1);
        }
    }

    @Test
    void promotingOnlyEverRaisesParticipants() {
        User participant = this.users.findOrCreate("p@example.com");
        User admin = this.users.findOrCreate("a@example.com");
        this.db.sql("UPDATE \"users\" SET \"role\" = 'ADMIN' WHERE \"id\" = ?").param(admin.id()).update();

        this.users.promoteParticipant(participant.id(), Role.STAFF);
        this.users.promoteParticipant(admin.id(), Role.STAFF);

        assertThat(this.users.findById(participant.id()).orElseThrow().role()).isEqualTo(Role.STAFF);
        assertThat(this.users.findById(admin.id()).orElseThrow().role()).isEqualTo(Role.ADMIN);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
