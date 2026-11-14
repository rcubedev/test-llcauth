package org.limelightcreatives.auth.oauth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.web.Visitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.limelightcreatives.auth.web.Visitor.body;

@AutoConfigureMockMvc
class DiscoveryTest extends DatabaseTest {

    private static final String ISSUER = "https://auth.limelightcreatives.org";

    @Autowired
    MockMvc mvc;

    @Test
    void openIdConfigurationIsPublic() throws Exception {
        MvcResult result = new Visitor(this.mvc).get("/.well-known/openid-configuration");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        String json = body(result);
        assertThat(JsonPath.<String>read(json, "$.issuer")).isEqualTo(ISSUER);
        assertThat(JsonPath.<String>read(json, "$.authorization_endpoint")).isEqualTo(ISSUER + "/oauth2/authorize");
        assertThat(JsonPath.<String>read(json, "$.token_endpoint")).isEqualTo(ISSUER + "/oauth2/token");
        assertThat(JsonPath.<String>read(json, "$.userinfo_endpoint")).isEqualTo(ISSUER + "/userinfo");
        assertThat(JsonPath.<String>read(json, "$.jwks_uri")).isEqualTo(ISSUER + "/oauth2/jwks");
        assertThat(JsonPath.<String>read(json, "$.end_session_endpoint")).isEqualTo(ISSUER + "/connect/logout");
        assertThat(JsonPath.<List<String>>read(json, "$.code_challenge_methods_supported")).contains("S256");
        assertThat(JsonPath.<List<String>>read(json, "$.id_token_signing_alg_values_supported")).containsExactly("ES256");
        assertThat(JsonPath.<List<String>>read(json, "$.scopes_supported")).contains("openid", "profile", "email");
    }

    @Test
    void theSigningKeysArePublic() throws Exception {
        MvcResult result = new Visitor(this.mvc).get("/oauth2/jwks");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(body(result), "$.keys[*].kty")).containsOnly("EC");
    }
}
