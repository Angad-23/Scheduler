package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.service.AccountException;
import com.sessionscheduler.scheduler.service.AccountService;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Public pages: "Forgot password?" and "Set your password" (from an invite or reset email). */
@Controller
public class PasswordController {
    private final AccountService accounts;

    public PasswordController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@RequestParam(name = "email", required = false) String email, RedirectAttributes flash) {
        if (email != null && !email.isBlank()) {
            accounts.startPasswordReset(email);
        }
        flash.addFlashAttribute("sentTo", AccountService.normalize(email));
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/set-password")
    public String setPage(@RequestParam(name = "token", required = false) String token, Model model) {
        Optional<AppUser> user = accounts.userForToken(token);
        model.addAttribute("token", token);
        model.addAttribute("valid", user.isPresent());
        user.ifPresent(u -> {
            model.addAttribute("name", u.getFullName());
            model.addAttribute("email", u.getEmail());
        });
        return "set-password";
    }

    @PostMapping("/set-password")
    public String set(@RequestParam("token") String token, @RequestParam("password") String password,
                      @RequestParam("confirm") String confirm, RedirectAttributes flash) {
        String error = null;
        if (password == null || password.length() < 8 || password.length() > 72) {
            error = "Use 8 to 72 characters.";
        } else if (!password.equals(confirm)) {
            error = "The two passwords don't match.";
        }
        if (error == null) {
            try {
                accounts.setPasswordWithToken(token, password);
                return "redirect:/login?passwordSet";
            } catch (AccountException e) {
                error = e.getMessage();
            }
        }
        flash.addFlashAttribute("error", error);
        flash.addAttribute("token", token);
        return "redirect:/set-password";
    }
}
