package org.limelightcreatives.auth.web;

import jakarta.servlet.http.Cookie;
import org.limelightcreatives.auth.RecordingMailer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

public class Visitor {

    public static final String COOKIE = "__Secure-authjs.session-token";

    private final MockMvc mvc;
    private final String ip;
    public Cookie session;

    public Visitor(MockMvc mvc) {
        this(mvc, "203.0.113.7");
    }

    public Visitor(MockMvc mvc, String ip) {
        this.mvc = mvc;
        this.ip = ip;
    }

    public MvcResult get(String url, String... params) throws Exception {
        var request = MockMvcRequestBuilders.get(URI.create(url));
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return send(request);
    }

    public MvcResult post(String url, String... params) throws Exception {
        var request = MockMvcRequestBuilders.post(url).with(csrf());
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return send(request);
    }

    public MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
        request.with(r -> {
            r.setRemoteAddr(this.ip);
            r.setSecure(true);
            r.setScheme("https");
            r.setServerName("auth.limelightcreatives.org");
            r.setServerPort(443);
            return r;
        });
        if (this.session != null) request.cookie(this.session);
        MvcResult result = this.mvc.perform(request).andReturn();
        Cookie set = result.getResponse().getCookie(COOKIE);
        if (set != null) this.session = set.getMaxAge() == 0 ? null : set;
        return result;
    }

    public MvcResult follow(MvcResult redirect) throws Exception {
        String location = redirect.getResponse().getRedirectedUrl();
        assertThat(location).as("a redirect").isNotNull();
        return get(location);
    }

    public MvcResult signIn(String email, RecordingMailer mailer) throws Exception {
        post("/login", "email", email);
        return post("/login/code", "code", mailer.lastCodeFor(email));
    }

    public boolean signedIn() throws Exception {
        String location = get("/login/continue").getResponse().getRedirectedUrl();
        return location == null || !location.endsWith("/login");
    }

    public static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    public static String location(MvcResult result) {
        MockHttpServletResponse response = result.getResponse();
        return response.getRedirectedUrl();
    }
}
