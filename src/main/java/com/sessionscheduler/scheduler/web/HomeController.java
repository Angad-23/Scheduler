package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.service.AccountException;
import com.sessionscheduler.scheduler.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public pages: landing, register, email verification, login. */
@Controller
public class HomeController {
    private final AccountService accounts;

    public HomeController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "register";
        }
        try {
            accounts.registerStudent(form.getFullName(), form.getEmail(), form.getPassword());
        } catch (AccountException e) {
            result.rejectValue("email", "taken", e.getMessage());
            return "register";
        }
        model.addAttribute("email", AccountService.normalize(form.getEmail()));
        return "check-email";
    }

    @GetMapping("/verify")
    public String verify(@RequestParam(name = "token", required = false) String token) {
        return accounts.verify(token) ? "redirect:/login?verified" : "redirect:/login?expired";
    }
}
