package org.limelightcreatives.auth.security;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.login.SignIn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailCodeAuthenticationProviderTest extends DatabaseTest {

    @Autowired
    AuthenticationManager authentication;

    @Autowired
    SignIn signIn;

    @Test
    void theRightCodeSignsYouInAsYourUserId() {
        this.signIn.sendCode("sam@example.com", "192.0.2.1");
        this.db.sql("UPDATE \"users\" SET \"role\" = 'STAFF'").update();
        String id = db.sql("SELECT \"id\" FROM \"users\"").query(String.class).single();

        Authentication result = this.authentication.authenticate(
                EmailCodeAuthenticationToken.unauthenticated("Sam@Example.com", " " + this.mailer.lastCodeFor("sam@example.com") + " "));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo(id);
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_STAFF");
        assertThat(result.getCredentials()).isNull();
    }

    @Test
    void aWrongCodeIsRefused() {
        this.signIn.sendCode("sam@example.com", "192.0.2.1");
        String wrong = this.mailer.lastCodeFor("sam@example.com").equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> this.authentication.authenticate(EmailCodeAuthenticationToken.unauthenticated("sam@example.com", wrong)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void unknownPeopleAreRefused() {
        assertThatThrownBy(() -> this.authentication.authenticate(EmailCodeAuthenticationToken.unauthenticated("who@example.com", "123456")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
