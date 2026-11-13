package org.limelightcreatives.auth.oauth;

import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class TokenClaims implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final Users users;

    public TokenClaims(Users users) {
        this.users = users;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        if (!idToken && !accessToken) return;

        context.getJwsHeader().algorithm(SignatureAlgorithm.ES256);

        User user = this.users.findById(context.getPrincipal().getName())
                .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
        context.getClaims().claims(claims -> claims.putAll(UserClaims.of(user)));
    }

    public Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfo() {
        return context -> {
            String id = context.getAuthorization().getPrincipalName();
            User user = this.users.findById(id)
                    .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_TOKEN));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", id);
            claims.putAll(UserClaims.of(user));
            return new OidcUserInfo(claims);
        };
    }
}
