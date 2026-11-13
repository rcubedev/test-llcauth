package org.limelightcreatives.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class SignOutOfApps implements LogoutHandler {

    private final JdbcClient db;

    public SignOutOfApps(JdbcClient db) {
        this.db = db;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, @Nullable Authentication authentication) {
        if (authentication == null) return;
        this.db.sql("DELETE FROM oauth2_authorization WHERE principal_name = ?").param(authentication.getName()).update();
    }
}
