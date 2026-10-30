package org.limelightcreatives.auth.web;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** What the host checks after each deploy. */
@AutoConfigureMockMvc
class HealthTest extends DatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void upWithoutDetails() throws Exception {
        var response = mvc.perform(get("/actuator/health")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .contains("\"status\":\"UP\"")
                .doesNotContain("components", "details", "db");
    }

    @Test
    void nothingElseIsExposed() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump"}) {
            assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isEqualTo(404);
        }
    }
}
