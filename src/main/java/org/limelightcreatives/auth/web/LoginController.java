package org.limelightcreatives.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.limelightcreatives.auth.invite.Invites;
import org.limelightcreatives.auth.login.SignIn;
import org.limelightcreatives.auth.session.Sessions;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The sign-in page, one step at a time: email, then code, then (first time only) name.
 *
 * <p>{@code next} is where to go afterwards (checked by {@link RedirectTargets}); {@code invite}
 * is an invite link's code. Both are carried through every step as hidden form fields.
 */
@Controller
public class LoginController {

    static final int MAX_NAME_LENGTH = 100;

    private final SignIn signIn;
    private final Sessions sessions;
    private final Users users;
    private final Invites invites;
    private final RedirectTargets redirects;

    public LoginController(SignIn signIn, Sessions sessions, Users users, Invites invites, RedirectTargets redirects) {
        this.signIn = signIn;
        this.sessions = sessions;
        this.users = users;
        this.invites = invites;
        this.redirects = redirects;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String next,
                        @RequestParam(required = false) String invite,
                        HttpServletRequest request, Model model) {
        var user = sessions.current(request);
        if (user.isPresent()) {
            // Already signed in (e.g. on another Limelight site): no need to ask for an email again.
            return afterSignIn(user.get(), next, invite, request, model);
        }
        return step("email", model, next, invite);
    }

    @PostMapping("/login/code")
    public String sendCode(@RequestParam String email,
                           @RequestParam(required = false) String next,
                           @RequestParam(required = false) String invite,
                           HttpServletRequest request, Model model) {
        model.addAttribute("email", Users.normalizeEmail(email));
        var result = signIn.sendCode(email, request.getRemoteAddr());
        switch (result) {
            case SENT -> { }
            case LOGGED_INSTEAD -> model.addAttribute("warning", result.message);
            default -> {
                model.addAttribute("error", result.message);
                return step("email", model, next, invite);
            }
        }
        return step("code", model, next, invite);
    }

    @PostMapping("/login/verify")
    public String verify(@RequestParam String email,
                         @RequestParam String code,
                         @RequestParam(required = false) String next,
                         @RequestParam(required = false) String invite,
                         HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("email", Users.normalizeEmail(email));
        var user = signIn.verify(email, code.trim());
        if (user.isEmpty()) {
            model.addAttribute("error", "That code didn't work. Check it and try again.");
            return step("code", model, next, invite);
        }
        sessions.signIn(response, user.get());
        return afterSignIn(user.get(), next, invite, request, model);
    }

    @PostMapping("/login/name")
    public String saveName(@RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam(required = false) String next,
                           HttpServletRequest request, Model model) {
        var user = sessions.current(request);
        if (user.isEmpty()) return "redirect:/login";

        String first = firstName.strip();
        String last = lastName.strip();
        if (first.isEmpty() || last.isEmpty() || first.length() > MAX_NAME_LENGTH || last.length() > MAX_NAME_LENGTH) {
            model.addAttribute("error", "First and last name are required");
            model.addAttribute("firstName", first);
            model.addAttribute("lastName", last);
            return step("name", model, next, null);
        }
        users.updateName(user.get().id(), first, last);
        return "redirect:" + redirects.safe(next);
    }

    /** Signed in: use the invite if there is one, then ask for a name if needed, then leave. */
    private String afterSignIn(User user, String next, String invite, HttpServletRequest request, Model model) {
        if (invite != null && !invite.isBlank()) {
            var result = invites.redeem(user.id(), invite, request.getRemoteAddr());
            if (result != Invites.Result.REDEEMED) {
                model.addAttribute("notice", result.message);
                return step("notice", model, next, null);
            }
        }
        if (user.needsProfile()) return step("name", model, next, null);
        return "redirect:" + redirects.safe(next);
    }

    private String step(String step, Model model, String next, String invite) {
        model.addAttribute("step", step);
        model.addAttribute("next", next);
        model.addAttribute("invite", invite == null || invite.isBlank() ? null : invite);
        return "login";
    }
}
