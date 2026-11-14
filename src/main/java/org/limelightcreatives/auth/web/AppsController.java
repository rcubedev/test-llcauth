package org.limelightcreatives.auth.web;

import org.limelightcreatives.auth.oauth.Apps;
import org.limelightcreatives.auth.user.Role;
import org.limelightcreatives.auth.user.User;
import org.limelightcreatives.auth.user.Users;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Set;

@Controller
@RequestMapping("/apps")
public class AppsController {

    private static final Set<Role> ALLOWED = Set.of(Role.STAFF, Role.ADMIN);

    private final Apps apps;
    private final Users users;

    public AppsController(Apps apps, Users users) {
        this.apps = apps;
        this.users = users;
    }

    @ModelAttribute
    void staffOnly(Principal principal) {
        Role role = this.users.findById(principal.getName()).map(User::role).orElse(null);
        if (!ALLOWED.contains(role)) throw new AccessDeniedException("Only staff can manage apps");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("apps", this.apps.list());
        return "apps/list";
    }

    @GetMapping("/new")
    public String add(Model model) {
        return editPage(null, AppForm.blank(), null, model);
    }

    @PostMapping
    public String create(AppForm form, Model model, RedirectAttributes redirect) {
        try {
            Apps.Created created = this.apps.create(form.settings());
            redirect.addFlashAttribute("secret", created.secret());
            return "redirect:/apps/" + created.app().id();
        } catch (Apps.InvalidApp e) {
            return editPage(null, form, e.getMessage(), model);
        }
    }

    @GetMapping("/{id}")
    public String show(@PathVariable String id, Model model) {
        Apps.App app = find(id);
        return editPage(app, AppForm.of(app.settings()), null, model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable String id, AppForm form, Model model, RedirectAttributes redirect) {
        Apps.App app = find(id);
        AppForm sameType = form.withPublicClient(app.settings().publicClient());
        try {
            this.apps.update(id, sameType.settings());
            redirect.addFlashAttribute("saved", true);
            return "redirect:/apps/" + id;
        } catch (Apps.InvalidApp e) {
            return editPage(app, sameType, e.getMessage(), model);
        }
    }

    @PostMapping("/{id}/secret")
    public String newSecret(@PathVariable String id, RedirectAttributes redirect) {
        if (find(id).settings().publicClient()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        redirect.addFlashAttribute("secret", this.apps.newSecret(id));
        return "redirect:/apps/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable String id) {
        find(id);
        this.apps.delete(id);
        return "redirect:/apps";
    }

    private static String editPage(Apps.App app, AppForm form, String error, Model model) {
        model.addAttribute("app", app);
        model.addAttribute("title", app == null ? "New app" : app.settings().name());
        model.addAttribute("form", form);
        model.addAttribute("error", error);
        return "apps/edit";
    }

    private Apps.App find(String id) {
        return this.apps.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
