package org.limelightcreatives.auth.oauth;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiredAuthorizationsTest extends DatabaseTest {

    @Autowired
    ExpiredAuthorizations expired;

    private void authorization(String id, String code, String access, String refresh) {
        this.db.sql("""
                INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,
                    authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at)
                VALUES (?, 'test-app', 'someone', 'authorization_code',
                    now() + ?::interval, now() + ?::interval, now() + ?::interval)
                """).params(id, code, access, refresh).update();
    }

    @Test
    void onlySignInsWithNothingLeftAreDeleted() {
        authorization("all-expired", "-31 days", "-31 days", "-1 day");
        authorization("refreshable", "-2 days", "-2 days", "28 days");
        authorization("abandoned-code", "-1 hour", null, null);
        authorization("fresh-code", "5 minutes", null, null);
        authorization("public-app-expired", "-1 day", "-1 day", null);
        authorization("in-progress", null, null, null);

        assertThat(expired.delete()).isEqualTo(3);
        assertThat(db.sql("SELECT id FROM oauth2_authorization ORDER BY id").query(String.class).list())
                .containsExactly("fresh-code", "in-progress", "refreshable");
    }
}
