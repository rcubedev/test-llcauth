package org.limelightcreatives.auth.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class Users {

    private static final String COLUMNS = "\"id\", \"email\", \"firstName\", \"lastName\", \"role\"::text AS \"role\"";

    private final JdbcClient db;
    private final Clock clock;

    public Users(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<User> findById(String id) {
        return this.db.sql("SELECT " + COLUMNS + " FROM \"users\" WHERE \"id\" = ?")
                .param(id).query(Users::map).optional();
    }

    public Optional<User> findByEmail(String email) {
        return this.db.sql("SELECT " + COLUMNS + " FROM \"users\" WHERE \"email\" = ?")
                .param(normaliseEmail(email)).query(Users::map).optional();
    }

    public User findOrCreate(String email) {
        String normalised = normaliseEmail(email);
        this.db.sql("""
                INSERT INTO "users" ("id", "email", "createdAt") VALUES (?, ?, ?)
                ON CONFLICT ("email") DO NOTHING
                """)
                .param(UUID.randomUUID().toString(), normalised, LocalDateTime.now(clock))
                .update();
        return findByEmail(normalised).orElseThrow();
    }

    public void updateName(String id, String firstName, String lastName) {
        this.db.sql("UPDATE \"users\" SET \"firstName\" = ?, \"lastName\" = ? WHERE \"id\" = ?")
                .params(firstName, lastName, id).update();
    }

    public void promoteParticipant(String id, Role role) {
        this.db.sql("UPDATE \"users\" SET \"role\" = ?::\"Role\" WHERE \"id\" = ? AND \"role\" = 'PARTICIPANT'")
                .params(role.name(), id).update();
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        return new User(rs.getString("id"), rs.getString("email"), rs.getString("firstName"),
                rs.getString("lastName"), Role.valueOf(rs.getString("role")));
    }
}
