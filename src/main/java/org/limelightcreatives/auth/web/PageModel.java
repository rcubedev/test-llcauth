package org.limelightcreatives.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

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

    @ModelAttribute("signedIn")
    public boolean signedIn(Principal principal) {
        return principal != null;
    }

    @ModelAttribute
    public void csrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
    }
}
