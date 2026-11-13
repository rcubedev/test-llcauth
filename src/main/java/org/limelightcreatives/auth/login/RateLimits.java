package org.limelightcreatives.auth.login;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class RateLimits {

    public static final String SEND_CODE = "send_code";
    public static final String INVITE_FAIL = "invite_fail";

    private final JdbcClient db;
    private final Clock clock;

    public RateLimits(JdbcClient db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public int count(String ip, String type, Duration window) {
        return this.db.sql("""
                SELECT count(*) FROM "rate_limit_events" WHERE "ip" = ? AND "type" = ? AND "createdAt" > ?
                """)
                .params(ip, type, LocalDateTime.now(this.clock).minus(window))
                .query(Integer.class).single();
    }

    public void lock(String ip, String type) {
        this.db.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").param(type + ":" + ip)
                .query((_, _) -> 0)
                .single();
    }

    public void record(String ip, String type) {
        this.db.sql("INSERT INTO \"rate_limit_events\" (\"id\", \"ip\", \"type\", \"createdAt\") VALUES (?, ?, ?, ?)")
                .params(UUID.randomUUID().toString(), ip, type, LocalDateTime.now(this.clock))
                .update();
    }
}
