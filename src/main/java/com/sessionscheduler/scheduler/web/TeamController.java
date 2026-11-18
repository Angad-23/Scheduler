package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.service.AccountException;
import com.sessionscheduler.scheduler.service.AccountService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Supervisors page: create supervisor logins by hand, set their passwords, deactivate and reactivate. */
@Controller
@RequestMapping("/admin/team")
public class TeamController {
    private final AccountService accounts;

    public TeamController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal UserDetails principal, Model model) {
        AppUser me = accounts.requireByEmail(principal.getUsername());
        Map<Long, String> states = new LinkedHashMap<>();
        var staff = accounts.staff();
        staff.forEach(u -> states.put(u.getId(), accounts.staffState(u)));
        model.addAttribute("user", me);
        model.addAttribute("staff", staff);
        model.addAttribute("states", states);
        return "team";
    }

    @PostMapping
    public String create(@RequestParam("fullName") String fullName, @RequestParam("email") String email,
                         @RequestParam("password") String password, RedirectAttributes flash) {
        String name = fullName == null ? "" : fullName.trim();
        String login = AccountService.normalize(email);
        if (name.isEmpty() || name.length() > 100
                || !login.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || login.length() > 190) {
            flash.addFlashAttribute("error", "Enter the supervisor's name and a full email address (their login ID).");
            return "redirect:/admin/team";
        }
        try {
            accounts.createSupervisor(name, login, password);
            flash.addFlashAttribute("ok", "Supervisor added. Share the login ID " + login
                + " and the password with them; they can change it after signing in.");
        } catch (AccountException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/team";
    }

    @PostMapping("/{id}/password")
    public String setPassword(@PathVariable("id") long id, @RequestParam("password") String password,
                              RedirectAttributes flash) {
        try {
            accounts.setSupervisorPassword(id, password);
            flash.addFlashAttribute("ok", "Password updated. Share the new password with the supervisor.");
        } catch (AccountException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/team";
    }

    @PostMapping("/{id}/active")
    public String setActive(@AuthenticationPrincipal UserDetails principal, @PathVariable("id") long id,
                            @RequestParam("active") boolean active, RedirectAttributes flash) {
        try {
            accounts.setStaffActive(id, active, accounts.requireByEmail(principal.getUsername()));
            flash.addFlashAttribute("ok", active
                ? "Account reactivated."
                : "Account deactivated. They can no longer sign in or receive booking emails.");
        } catch (AccountException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/team";
    }
}
