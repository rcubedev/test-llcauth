package org.limelightcreatives.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.limelightcreatives.auth.AuthProperties;
import org.limelightcreatives.auth.invite.Invites;
import org.limelightcreatives.auth.login.SignIn;
import org.limelightcreatives.auth.security.EmailCodeAuthenticationProvider;
import org.limelightcreatives.auth.security.EmailCodeAuthenticationToken;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Optional;

@Controller
public class LoginController {

    static final String NEXT = "login.next";
    static final String INVITE = "login.invite";
    static final String EMAIL = "login.email";

    static final String WRONG_CODE = "That code didn't work. Check it and try again.";
    static final int MAX_NAME_LENGTH = 100;

    private final SignIn signIn;
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final RequestCache requestCache;
    private final Users users;
    private final Invites invites;
    private final RedirectTargets redirects;
    private final AuthProperties properties;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public LoginController(SignIn signIn, AuthenticationManager authentication, SecurityContextRepository contexts,
                           RequestCache requestCache, Users users, Invites invites, RedirectTargets redirects,
                           AuthProperties properties) {
        this.signIn = signIn;
        this.authentication = authentication;
        this.contexts = contexts;
        this.requestCache = requestCache;
        this.users = users;
        this.invites = invites;
        this.redirects = redirects;
        this.properties = properties;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String next, @RequestParam(required = false) String invite,
                        Principal principal, HttpServletRequest request, HttpServletResponse response, Model model) {
        if (next != null) {
            request.getSession().setAttribute(NEXT, next);
            this.requestCache.removeRequest(request, response);
        }
        if (invite != null && !invite.isBlank()) request.getSession().setAttribute(INVITE, invite.strip());

        if (principal != null) return "redirect:/login/continue";

        model.addAttribute("invite", attribute(request, INVITE).isPresent());
        return "login/email";
    }

    @PostMapping("/login")
    public String sendCode(@RequestParam String email, HttpServletRequest request, RedirectAttributes flash) {
        String normalised = Users.normaliseEmail(email);
        SignIn.SendResult result = this.signIn.sendCode(email, request.getRemoteAddr());
        switch (result) {
            case SENT -> { }
            case LOGGED_INSTEAD -> flash.addFlashAttribute("warning", result.message);
            default -> {
                flash.addFlashAttribute("error", result.message);
                flash.addFlashAttribute("email", normalised);
                return "redirect:/login";
            }
        }
        request.getSession().setAttribute(EMAIL, normalised);
        return "redirect:/login/code";
    }

    @GetMapping("/login/code")
    public String codeForm(HttpServletRequest request, Model model) {
        Optional<String> email = attribute(request, EMAIL);
        if (email.isEmpty()) return "redirect:/login";
        model.addAttribute("email", email.get());
        return "login/code";
    }

    @PostMapping("/login/code")
    public String verify(@RequestParam String code, HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        Optional<String> email = attribute(request, EMAIL);
        if (email.isEmpty()) return "redirect:/login";

        Authentication signedIn;
        try {
            signedIn = this.authentication.authenticate(EmailCodeAuthenticationToken.unauthenticated(email.get(), code));
        } catch (AuthenticationException e) {
            flash.addFlashAttribute("error", WRONG_CODE);
            return "redirect:/login/code";
        }

        request.changeSessionId();
        HttpSession session = request.getSession();
        session.removeAttribute(EMAIL);
        session.setMaxInactiveInterval((int) this.properties.sessionLifetime().toSeconds());
        saveSignIn(signedIn, request, response);
        return "redirect:/login/continue";
    }

    @GetMapping("/login/continue")
    public String afterSignIn(Principal principal, HttpServletRequest request, HttpServletResponse response, Model model) {
        Optional<User> user = this.users.findById(principal.getName());
        if (user.isEmpty()) {
            request.getSession().invalidate();
            return "redirect:/login";
        }

        Optional<String> invite = attribute(request, INVITE);
        if (invite.isPresent()) {
            request.getSession().removeAttribute(INVITE);
            Invites.Result result = this.invites.redeem(user.get().id(), invite.get(), request.getRemoteAddr());
            if (result != Invites.Result.REDEEMED) {
                model.addAttribute("notice", result.message);
                return "login/notice";
            }
            user = this.users.findById(user.get().id());
            Authentication current = this.holder.getContext().getAuthentication();
            saveSignIn(EmailCodeAuthenticationProvider.refreshed(current, user.orElseThrow()), request, response);
        }
        if (user.orElseThrow().needsProfile()) return "redirect:/login/name";
        return "redirect:" + destination(request, response);
    }

    @GetMapping("/login/name")
    public String nameForm(Principal principal) {
        boolean named = this.users.findById(principal.getName()).map(user -> !user.needsProfile()).orElse(false);
        return named ? "redirect:/login/continue" : "login/name";
    }

    @PostMapping("/login/name")
    public String saveName(@RequestParam String firstName, @RequestParam String lastName, Principal principal, Model model) {
        String first = firstName.strip();
        String last = lastName.strip();
        if (first.isEmpty() || last.isEmpty() || first.length() > MAX_NAME_LENGTH || last.length() > MAX_NAME_LENGTH) {
            model.addAttribute("error", "First and last name are required");
            model.addAttribute("firstName", first);
            model.addAttribute("lastName", last);
            return "login/name";
        }
        this.users.updateName(principal.getName(), first, last);
        return "redirect:/login/continue";
    }

    String destination(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = this.requestCache.getRequest(request, response);
        if (saved != null) {
            this.requestCache.removeRequest(request, response);
            return saved.getRedirectUrl();
        }
        Optional<String> next = attribute(request, NEXT);
        request.getSession().removeAttribute(NEXT);
        return this.redirects.safe(next.orElse(null));
    }

    private void saveSignIn(Authentication signedIn, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = this.holder.createEmptyContext();
        context.setAuthentication(signedIn);
        this.holder.setContext(context);
        this.contexts.saveContext(context, request, response);
    }

    private static Optional<String> attribute(HttpServletRequest request, String name) {
        HttpSession session = request.getSession(false);
        return Optional.ofNullable(session == null ? null : (String) session.getAttribute(name));
    }
}
