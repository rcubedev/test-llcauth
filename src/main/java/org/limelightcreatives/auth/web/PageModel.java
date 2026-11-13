package org.limelightcreatives.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PageModel {

    private final Assets assets;

    public PageModel(Assets assets) {
        this.assets = assets;
    }

    @ModelAttribute("assets")
    public Assets assets() {
        return this.assets;
    }

    @ModelAttribute
    public void csrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
    }
}
