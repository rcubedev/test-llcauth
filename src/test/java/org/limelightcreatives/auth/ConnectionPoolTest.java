package org.limelightcreatives.auth;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolTest extends DatabaseTest {

    @Autowired
    DataSource dataSource;

    @Test
    void idleConnectionsClose() {
        HikariDataSource pool = (HikariDataSource) dataSource;
        assertThat(pool.getMinimumIdle()).isZero();
        assertThat(pool.getIdleTimeout()).isLessThanOrEqualTo(60_000);
        assertThat(pool.getMaximumPoolSize()).isLessThanOrEqualTo(5);
    }
}
