package org.limelightcreatives.auth.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** The "users" table. Emails are stored lower-case and trimmed (see {@link #normalizeEmail}). */
@Repository
public class Users {

    private static final String COLUMNS = "\"id\", \"email\", \"firstName\", \"lastName\", \"role\"::text AS \"role\"";

    private final JdbcClient db;
    private final Clock clock;

    public Users(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public Optional<User> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM \"users\" WHERE \"id\" = ?")
                .param(id).query(Users::map).optional();
    }

    public Optional<User> findByEmail(String email) {
        return db.sql("SELECT " + COLUMNS + " FROM \"users\" WHERE \"email\" = ?")
                .param(normalizeEmail(email)).query(Users::map).optional();
    }

    /** The user with this email, created as a participant if there isn't one yet. */
    public User findOrCreate(String email) {
        String normalized = normalizeEmail(email);
        // ON CONFLICT: two sign-ups for the same new email at once both end up with the same row.
        db.sql("""
                INSERT INTO "users" ("id", "email", "createdAt") VALUES (?, ?, ?)
                ON CONFLICT ("email") DO NOTHING
                """)
                .params(UUID.randomUUID().toString(), normalized, LocalDateTime.now(clock))
                .update();
        return findByEmail(normalized).orElseThrow();
    }

    public void updateName(String id, String firstName, String lastName) {
        db.sql("UPDATE \"users\" SET \"firstName\" = ?, \"lastName\" = ? WHERE \"id\" = ?")
                .params(firstName, lastName, id).update();
    }

    /** Gives a participant a new role. Never changes staff or admins, so an invite can't downgrade anyone. */
    public void promoteParticipant(String id, Role role) {
        db.sql("UPDATE \"users\" SET \"role\" = ?::\"Role\" WHERE \"id\" = ? AND \"role\" = 'PARTICIPANT'")
                .params(role.name(), id).update();
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        return new User(rs.getString("id"), rs.getString("email"), rs.getString("firstName"),
                rs.getString("lastName"), Role.valueOf(rs.getString("role")));
    }
}
