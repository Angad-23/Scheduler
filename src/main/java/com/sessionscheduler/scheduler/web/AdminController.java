package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.Proctor;
import com.sessionscheduler.scheduler.repo.NotificationRepository;
import com.sessionscheduler.scheduler.repo.ProctorRepository;
import com.sessionscheduler.scheduler.service.AccountService;
import com.sessionscheduler.scheduler.service.BookingException;
import com.sessionscheduler.scheduler.service.BookingService;
import com.sessionscheduler.scheduler.service.ExcelExportService;
import com.sessionscheduler.scheduler.service.TimeFormat;
import com.sessionscheduler.scheduler.model.ExamSession;
import com.sessionscheduler.scheduler.model.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Supervisor portal: all sessions, today's sessions, notifications, proctor allotment, Excel export. */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AccountService accounts;
    private final BookingService bookings;
    private final ExcelExportService excel;
    private final ProctorRepository proctors;
    private final NotificationRepository notifications;
    private final TimeFormat fmt;

    public AdminController(AccountService accounts, BookingService bookings, ExcelExportService excel,
                           ProctorRepository proctors, NotificationRepository notifications, TimeFormat fmt) {
        this.accounts = accounts;
        this.bookings = bookings;
        this.excel = excel;
        this.proctors = proctors;
        this.notifications = notifications;
        this.fmt = fmt;
    }

    @GetMapping
    public String home(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam(name = "view", defaultValue = "upcoming") String view, Model model) {
        AppUser me = accounts.requireByEmail(principal.getUsername());
        Instant now = Instant.now();
        List<ExamSession> all = bookings.all();
        List<ExamSession> upcoming = all.stream()
            .filter(s -> s.getStatus() != SessionStatus.CANCELLED && s.getEndsAt().isAfter(now)).toList();
        List<ExamSession> waiting = upcoming.stream()
            .filter(s -> s.getStatus() == SessionStatus.REQUESTED).toList();
        List<ExamSession> today = bookings.today();
        String current = switch (view) {
            case "waiting", "all" -> view;
            default -> "upcoming";
        };
        model.addAttribute("user", me);
        model.addAttribute("view", current);
        model.addAttribute("sessions", switch (current) {
            case "waiting" -> waiting;
            case "all" -> all.reversed();
            default -> upcoming;
        });
        model.addAttribute("countWaiting", waiting.size());
        model.addAttribute("countUpcoming", upcoming.size());
        model.addAttribute("countAll", all.size());
        model.addAttribute("today", today);
        model.addAttribute("todayWaiting", today.stream().filter(s -> s.getStatus() == SessionStatus.REQUESTED).count());
        model.addAttribute("now", now);
        model.addAttribute("proctors", proctors.findByActiveTrueOrderByFullNameAsc());
        model.addAttribute("unread", notifications.countByRecipientAndReadFlagFalse(me));
        model.addAttribute("notifications", notifications.findTop20ByRecipientOrderByCreatedAtDesc(me));
        model.addAttribute("displayZone", fmt.displayZone().getId());
        return "admin";
    }

    @PostMapping("/sessions/{id}/assign")
    public String assign(@PathVariable("id") long id,
                         @RequestParam(name = "proctorId", required = false) Long proctorId,
                         RedirectAttributes flash) {
        if (proctorId == null) {
            flash.addFlashAttribute("error", "Choose a proctor first.");
            return "redirect:/admin";
        }
        try {
            bookings.assign(id, proctorId);
            flash.addFlashAttribute("ok", "Proctor assigned. The student and the proctor have been emailed.");
        } catch (BookingException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> export() {
        byte[] file = excel.export(bookings.all());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("sessions-" + LocalDate.now(fmt.displayZone()) + ".xlsx").build());
        return ResponseEntity.ok().headers(headers).body(file);
    }

    /** Polled by the portal page to keep the unread badge current. */
    @GetMapping("/notifications/count")
    @ResponseBody
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserDetails me) {
        return Map.of("count",
            notifications.countByRecipientAndReadFlagFalse(accounts.requireByEmail(me.getUsername())));
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public String markRead(@AuthenticationPrincipal UserDetails me) {
        notifications.markAllRead(accounts.requireByEmail(me.getUsername()));
        return "redirect:/admin";
    }

    @GetMapping("/proctors")
    public String proctorsPage(@AuthenticationPrincipal UserDetails me, Model model) {
        model.addAttribute("user", accounts.requireByEmail(me.getUsername()));
        model.addAttribute("proctors", proctors.findAllByOrderByFullNameAsc());
        return "proctors";
    }

    @PostMapping("/proctors")
    public String addProctor(@RequestParam("fullName") String fullName, @RequestParam("email") String email,
                             RedirectAttributes flash) {
        String name = fullName.trim();
        String mail = AccountService.normalize(email);
        if (name.isEmpty() || name.length() > 100 || !mail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || mail.length() > 190) {
            flash.addFlashAttribute("error", "Enter the proctor's name and a full email address.");
        } else {
            proctors.save(new Proctor(name, mail));
            flash.addFlashAttribute("ok", name + " added.");
        }
        return "redirect:/admin/proctors";
    }

    @PostMapping("/proctors/{id}/toggle")
    public String toggleProctor(@PathVariable("id") long id) {
        proctors.findById(id).ifPresent(p -> {
            p.setActive(!p.isActive());
            proctors.save(p);
        });
        return "redirect:/admin/proctors";
    }
}
