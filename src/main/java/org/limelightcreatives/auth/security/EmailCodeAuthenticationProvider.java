package org.limelightcreatives.auth.security;

import org.jspecify.annotations.Nullable;
import org.limelightcreatives.auth.login.SignIn;
import org.limelightcreatives.auth.user.User;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
public class EmailCodeAuthenticationProvider implements AuthenticationProvider {

    private final SignIn signIn;
    private final Clock clock;

    public EmailCodeAuthenticationProvider(SignIn signIn, Clock clock) {
        this.signIn = signIn;
        this.clock = clock;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        EmailCodeAuthenticationToken attempt = (EmailCodeAuthenticationToken) authentication;
        String code = attempt.getCredentials() == null ? "" : attempt.getCredentials().strip();
        User user = this.signIn.verify(attempt.getPrincipal(), code)
                .orElseThrow(() -> new BadCredentialsException("That code didn't work. Check it and try again."));
        FactorGrantedAuthority factor = FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.OTT_AUTHORITY)
                .issuedAt(Instant.now(this.clock))
                .build();
        return signedIn(user, factor);
    }

    public static Authentication signedIn(User user, FactorGrantedAuthority factor) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()), factor);
        return UsernamePasswordAuthenticationToken.authenticated(user.id(), null, authorities);
    }

    public static Authentication refreshed(Authentication current, User user) {
        FactorGrantedAuthority factor = current.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance).map(FactorGrantedAuthority.class::cast)
                .findFirst().orElseThrow();
        return signedIn(user, factor);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return EmailCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
