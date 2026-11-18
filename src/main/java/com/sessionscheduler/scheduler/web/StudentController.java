package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.service.AccountService;
import com.sessionscheduler.scheduler.service.BookingException;
import com.sessionscheduler.scheduler.service.BookingService;
import com.sessionscheduler.scheduler.service.TimeFormat;
import com.sessionscheduler.scheduler.service.UsTimeZones;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class StudentController {
    private static final String FORM = "bookingForm";

    private final AccountService accounts;
    private final BookingService bookings;
    private final TimeFormat fmt;

    public StudentController(AccountService accounts, BookingService bookings, TimeFormat fmt) {
        this.accounts = accounts;
        this.bookings = bookings;
        this.fmt = fmt;
    }

    @GetMapping
    public String home(@AuthenticationPrincipal UserDetails me, Model model) {
        // After a failed booking the form and its errors arrive here from the session (flash attributes).
        if (!model.containsAttribute(FORM)) {
            BookingForm form = new BookingForm();
            form.setDate(LocalDate.now(fmt.displayZone()).plusDays(1));
            form.setTimeZone(UsTimeZones.DEFAULT);
            model.addAttribute(FORM, form);
        }
        AppUser student = accounts.requireByEmail(me.getUsername());
        model.addAttribute("user", student);
        model.addAttribute("zones", UsTimeZones.options());
        java.time.Instant now = java.time.Instant.now();
        java.util.List<com.sessionscheduler.scheduler.model.ExamSession> mine = bookings.forStudent(student);
        model.addAttribute("upcoming", mine.stream()
            .filter(s -> s.getStatus() != com.sessionscheduler.scheduler.model.SessionStatus.CANCELLED && s.getEndsAt().isAfter(now))
            .toList());
        model.addAttribute("history", mine.stream()
            .filter(s -> s.getStatus() == com.sessionscheduler.scheduler.model.SessionStatus.CANCELLED || !s.getEndsAt().isAfter(now))
            .sorted(java.util.Comparator.comparing(com.sessionscheduler.scheduler.model.ExamSession::getStartsAt).reversed())
            .toList());
        return "student";
    }

    /** Post/Redirect/Get: success and failure both redirect, so a refresh never submits twice. */
    @PostMapping("/sessions")
    public String book(@AuthenticationPrincipal UserDetails me,
                       @Valid @ModelAttribute(FORM) BookingForm form, BindingResult result,
                       RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                bookings.book(accounts.requireByEmail(me.getUsername()), form.getDate(), form.getTime(),
                    form.getTimeZone());
                flash.addFlashAttribute("ok", "Your request has been received. We've emailed you a confirmation and will get back to you as soon as possible.");
                return "redirect:/student";
            } catch (BookingException e) {
                result.reject("booking", e.getMessage());
            }
        }
        flash.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + FORM, result);
        flash.addFlashAttribute(FORM, form);
        return "redirect:/student";
    }

    @PostMapping("/sessions/{id}/cancel")
    public String cancel(@AuthenticationPrincipal UserDetails me, @PathVariable("id") long id,
                         RedirectAttributes flash) {
        try {
            bookings.cancel(accounts.requireByEmail(me.getUsername()), id);
            flash.addFlashAttribute("ok", "Booking cancelled.");
        } catch (BookingException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student";
    }
}
