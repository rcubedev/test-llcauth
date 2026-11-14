package org.limelightcreatives.auth.oauth;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class App {

    static final String ISSUER = "https://auth.limelightcreatives.org";
    private static final SecureRandom RANDOM = new SecureRandom();

    final String clientId;
    final String secret;
    final String redirectUri;
    final String verifier = random();
    final String state = random();
    final String nonce = random();
    private final MockMvc mvc;

    App(MockMvc mvc, String clientId, String secret, String redirectUri) {
        this.mvc = mvc;
        this.clientId = clientId;
        this.secret = secret;
        this.redirectUri = redirectUri;
    }

    static App confidential(MockMvc mvc) {
        return new App(mvc, "test-app", "test-app-secret-test-app-secret-123", "https://app.example/callback");
    }

    static App browser(MockMvc mvc) {
        return new App(mvc, "test-spa", null, "https://spa.example/callback");
    }

    String authorizeUrl() {
        return authorizeUrl(Map.of());
    }

    String authorizeUrl(Map<String, String> changes) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(ISSUER + "/oauth2/authorize");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", this.clientId);
        params.put("scope", "openid profile email");
        params.put("redirect_uri", this.redirectUri);
        params.put("state", this.state);
        params.put("nonce", this.nonce);
        params.put("code_challenge", challenge(this.verifier));
        params.put("code_challenge_method", "S256");
        params.putAll(changes);
        params.forEach((name, value) -> {
            if (value != null) url.queryParam(name, value);
        });
        return url.encode().toUriString();
    }

    String code(MvcResult callback) {
        String location = callback.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(this.redirectUri + "?");
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(params.getFirst("state")).isEqualTo(this.state);
        return params.getFirst("code");
    }

    MvcResult token(String code) throws Exception {
        return tokenRequest("grant_type", "authorization_code", "code", code, "redirect_uri", this.redirectUri,
                "code_verifier", this.verifier);
    }

    MvcResult refresh(String refreshToken) throws Exception {
        return tokenRequest("grant_type", "refresh_token", "refresh_token", refreshToken);
    }

    MvcResult tokenRequest(String... params) throws Exception {
        MockHttpServletRequestBuilder request = post("/oauth2/token").secure(true);
        if (this.secret != null) {
            String basic = Base64.getEncoder().encodeToString((this.clientId + ":" + this.secret).getBytes(StandardCharsets.UTF_8));
            request.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
        } else {
            request.param("client_id", this.clientId);
        }
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return this.mvc.perform(request).andReturn();
    }

    MvcResult userInfo(String accessToken) throws Exception {
        return this.mvc.perform(get("/userinfo").secure(true).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andReturn();
    }

    JWTClaimsSet verified(String jwt) throws Exception {
        String jwks = this.mvc.perform(get("/oauth2/jwks").secure(true)).andReturn().getResponse().getContentAsString();
        SignedJWT token = SignedJWT.parse(jwt);
        JWK key = JWKSet.parse(jwks).getKeyByKeyId(token.getHeader().getKeyID());
        assertThat(key).as("signed with a published key").isNotNull();
        assertThat(token.verify(key instanceof RSAKey rsa ? new RSASSAVerifier(rsa) : new ECDSAVerifier(key.toECKey()))).isTrue();
        return token.getJWTClaimsSet();
    }

    static JWSAlgorithm algorithm(String jwt) throws Exception {
        return SignedJWT.parse(jwt).getHeader().getAlgorithm();
    }

    static String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
