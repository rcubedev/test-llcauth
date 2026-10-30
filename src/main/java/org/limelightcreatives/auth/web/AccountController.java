package org.limelightcreatives.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.session.Sessions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@code /account}: who you're signed in as, with a sign-out button. Mostly for when this app runs
 * on its own (e.g. a test copy with no dashboard to go back to); set AUTH_DEFAULT_REDIRECT to it there.
 */
@Controller
public class AccountController {

    private final Sessions sessions;
    private final AuthProperties properties;

    public AccountController(Sessions sessions, AuthProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @GetMapping("/account")
    public String account(HttpServletRequest request, Model model) {
        var user = sessions.current(request);
        if (user.isEmpty()) {
            String here = properties.baseUrl().resolve("/account").toString();
            return "redirect:" + UriComponentsBuilder.fromPath("/login").queryParam("next", here).encode().toUriString();
        }
        model.addAttribute("user", user.get());
        return "account";
    }

    /** Signs out of every Limelight site (the cookie is shared). */
    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        sessions.signOut(response);
        return "redirect:/login";
    }
}
