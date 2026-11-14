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

    @ModelAttribute("page")
    public Page page(HttpServletRequest request, Principal principal) {
        CsrfToken csrf = request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token ? token : null;
        if (csrf != null) csrf.getToken();
        return new Page(this.assets, csrf, principal != null);
    }
}
