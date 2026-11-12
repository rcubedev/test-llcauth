package org.limelightcreatives.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public final class EmailCodeAuthenticationToken extends AbstractAuthenticationToken {

    private final String email;
    private transient String code;

    public EmailCodeAuthenticationToken(String email, String code) {
        super(List.of());
        this.email = email;
        this.code = code;
        setAuthenticated(false);
    }

    public static EmailCodeAuthenticationToken unauthenticated(String email, String code) {
        return new EmailCodeAuthenticationToken(email, code);
    }

    @Override
    public String getPrincipal() {
        return this.email;
    }

    @Override
    public String getCredentials() {
        return this.code;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.code = null;
    }
}
