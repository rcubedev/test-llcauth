package org.limelightcreatives.auth.security;

import org.jspecify.annotations.Nullable;
import org.limelightcreatives.auth.login.SignIn;
import org.limelightcreatives.auth.user.User;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class EmailCodeAuthenticationProvider implements AuthenticationProvider {

    private final SignIn signIn;

    public EmailCodeAuthenticationProvider(SignIn signIn) {
        this.signIn = signIn;
    }

    @Override
    public @Nullable Authentication authenticate(Authentication authentication) throws AuthenticationException {
        EmailCodeAuthenticationToken attempt = (EmailCodeAuthenticationToken) authentication;
        String code = attempt.getCredentials() == null ? "" : attempt.getCredentials().strip();
        User user = this.signIn.verify(attempt.getPrincipal(), code)
                .orElseThrow(() -> new BadCredentialsException("That code didn't work. Check it and try again."));
        return signedIn(user);
    }

    public static Authentication signedIn(User user) {
        SimpleGrantedAuthority role = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        return UsernamePasswordAuthenticationToken.authenticated(user.id(), null, List.of(role));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return EmailCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
