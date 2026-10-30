package org.limelightcreatives.auth.web;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A pretend browser for MockMvc: keeps the session cookie between requests, like a real one. */
class Visitor {

    static final String COOKIE = "__Secure-authjs.session-token";

    private final MockMvc mvc;
    private final String ip;
    Cookie session;

    Visitor(MockMvc mvc) {
        this(mvc, "203.0.113.7");
    }

    Visitor(MockMvc mvc, String ip) {
        this.mvc = mvc;
        this.ip = ip;
    }

    MvcResult get(String url, String... params) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return send(request);
    }

    MvcResult post(String url, String... params) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Origin", "https://auth.limelightcreatives.org");
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return send(request);
    }

    private MvcResult send(MockHttpServletRequestBuilder request) throws Exception {
        request.with(r -> {
            r.setRemoteAddr(ip);
            r.setSecure(true);
            return r;
        });
        if (session != null) request.cookie(session);
        MvcResult result = mvc.perform(request).andReturn();
        Cookie set = result.getResponse().getCookie(COOKIE);
        if (set != null) session = set.getMaxAge() == 0 ? null : set;
        return result;
    }

    /** Signs in with the code the mailer recorded. */
    MvcResult signIn(String email, String code, String... extra) throws Exception {
        String[] params = new String[4 + extra.length];
        params[0] = "email"; params[1] = email; params[2] = "code"; params[3] = code;
        System.arraycopy(extra, 0, params, 4, extra.length);
        return post("/login/verify", params);
    }

    static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
