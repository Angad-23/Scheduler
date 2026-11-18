package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.Role;
import com.sessionscheduler.scheduler.service.AccountException;
import com.sessionscheduler.scheduler.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Change password for the logged-in user. Not on the public list, so it can't be opened after logout. */
@Controller
@RequestMapping("/account/password")
public class AccountController {
    private static final String FORM = "passwordForm";

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal UserDetails me, Model model) {
        model.addAttribute(FORM, new PasswordForm());
        AppUser user = accounts.requireByEmail(me.getUsername());
        model.addAttribute("user", user);
        model.addAttribute("home", user.getRole() == Role.STUDENT ? "/student" : "/admin");
        return "account-password";
    }

    @PostMapping
    public String change(@AuthenticationPrincipal UserDetails me,
                         @Valid @ModelAttribute(FORM) PasswordForm form, BindingResult result,
                         RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                accounts.changePassword(me.getUsername(), form.getCurrentPassword(), form.getNewPassword());
                flash.addFlashAttribute("ok", "Password changed. Use the new one next time you log in.");
                return "redirect:/account/password";
            } catch (AccountException e) {
                result.rejectValue("currentPassword", "password", e.getMessage());
            }
        }
        // Post/Redirect/Get: only the error messages go into the session, never the typed passwords.
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError e : result.getFieldErrors()) {
            String field = "confirmed".equals(e.getField()) ? "confirmPassword" : e.getField();
            errors.putIfAbsent(field, e.getDefaultMessage());
        }
        flash.addFlashAttribute("errors", errors);
        return "redirect:/account/password";
    }
}
