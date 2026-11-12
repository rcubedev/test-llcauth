package org.limelightcreatives.auth.web;

import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.Optional;

@Controller
public class AccountController {

    private final Users users;

    public AccountController(Users users) {
        this.users = users;
    }

    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        Optional<User> user = this.users.findById(principal.getName());
        if (user.isEmpty()) return "redirect:/login/continue";
        if (user.get().needsProfile()) return "redirect:/login/name";
        model.addAttribute("user", user.get());
        return "account";
    }
}
