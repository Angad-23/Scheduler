package com.sessionscheduler.scheduler.service;

import com.sessionscheduler.scheduler.config.AppProperties;
import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.ExamSession;
import com.sessionscheduler.scheduler.model.Notification;
import com.sessionscheduler.scheduler.model.Proctor;
import com.sessionscheduler.scheduler.model.Role;
import com.sessionscheduler.scheduler.model.SessionStatus;
import com.sessionscheduler.scheduler.repo.ExamSessionRepository;
import com.sessionscheduler.scheduler.repo.NotificationRepository;
import com.sessionscheduler.scheduler.repo.ProctorRepository;
import com.sessionscheduler.scheduler.repo.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Students tell us when they will take their test (date, start time, US time zone).
 * Emails: when a student books, every active supervisor gets "New test booking" and the student gets
 * "We've received your request". Nothing else is emailed: assigning a proctor emails no one.
 */
@Service
public class BookingService {
    /** Stored on every booking; students no longer name the exam. */
    static final String DEFAULT_TITLE = "Proctored test";

    private final ExamSessionRepository sessions;
    private final ProctorRepository proctors;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final MailService mail;
    private final TimeFormat fmt;
    private final String baseUrl;
    private final int sessionMinutes;
    private final String brandName;

    public BookingService(ExamSessionRepository sessions, ProctorRepository proctors, UserRepository users,
                          NotificationRepository notifications, MailService mail, TimeFormat fmt,
                          AppProperties props) {
        this.sessions = sessions;
        this.proctors = proctors;
        this.users = users;
        this.notifications = notifications;
        this.mail = mail;
        this.fmt = fmt;
        this.baseUrl = props.baseUrl();
        this.sessionMinutes = props.sessionLength();
        this.brandName = props.brandNameOrDefault();
    }

    /** Saves the student's test time, then tells every supervisor on the platform and by email. */
    @Transactional
    public ExamSession book(AppUser student, LocalDate date, LocalTime time, String timeZone) {
        if (!UsTimeZones.isAllowed(timeZone)) {
            throw new BookingException("Choose your time zone from the list.");
        }
        ZoneId zone = ZoneId.of(timeZone);
        Instant start = ZonedDateTime.of(date, time, zone).toInstant();
        if (start.isBefore(Instant.now())) {
            throw new BookingException("That time has already passed. Choose a later date or time.");
        }
        Instant end = start.plusSeconds(sessionMinutes * 60L);
        for (ExamSession other : sessions.findByStudentOrderByStartsAtAsc(student)) {
            if (other.getStatus() != SessionStatus.CANCELLED
                    && other.getStartsAt().isBefore(end) && other.getEndsAt().isAfter(start)) {
                throw new BookingException("You already have a test booked close to that time ("
                    + fmt.inZone(other.getStartsAt(), other.getTimeZone()) + ").");
            }
        }

        ExamSession s = new ExamSession();
        s.setStudent(student);
        s.setTitle(DEFAULT_TITLE);
        s.setStartsAt(start);
        s.setTimeZone(zone.getId());
        s.setDurationMinutes(sessionMinutes);
        s = sessions.save(s);

        String studentWhen = fmt.inZone(start, zone.getId());
        String portalDay = fmt.displayPart(start, "EEE d MMM");
        notifyStaff(student.getFullName() + " booked a test for " + studentWhen + ".");
        for (AppUser staff : staff()) {
            mail.send(MailMessage.to(staff.getEmail())
                .subject("New test booking: " + student.getFullName() + " on " + portalDay)
                .preheader(student.getFullName() + " will test on " + studentWhen + ". No proctor assigned yet.")
                .heading("New test booking")
                .status("Not assigned", MailMessage.Tone.WAITING)
                .paragraph(student.getFullName() + " has booked a test time. Assign a proctor from the supervisor portal.")
                .detail("Student", student.getFullName() + " (" + student.getEmail() + ")")
                .detail("Date", fmt.displayPart(start, "EEEE d MMMM yyyy"))
                .detail("Time", fmt.displayPart(start, "HH:mm") + " " + fmt.displayAbbr(start))
                .detail("Student's time", studentWhen)
                .button("Assign a proctor", baseUrl + "/admin?view=waiting")
                .reason("You're receiving this email because you're a supervisor on " + brandName + "."));
        }
        mail.send(MailMessage.to(student.getEmail())
            .subject("We've received your test request")
            .preheader("Your request for " + studentWhen + " has been received. We'll get back to you as soon as possible.")
            .heading("We've received your request")
            .greeting("Hi " + AccountService.firstName(student.getFullName()) + ",")
            .paragraph("Your test request has been received successfully. Our team will get back to you as soon as possible.")
            .detail("Date", fmt.part(start, zone.getId(), "EEEE d MMMM yyyy"))
            .detail("Time", fmt.part(start, zone.getId(), "h:mm a") + " " + fmt.abbr(start, zone.getId()))
            .button("View my bookings", baseUrl + "/student")
            .footnote("Need a different time? Cancel this booking from My bookings and choose a new one."));
        return s;
    }

    @Transactional
    public void cancel(AppUser student, long sessionId) {
        ExamSession s = sessions.findById(sessionId)
            .orElseThrow(() -> new BookingException("Booking not found."));
        if (!s.getStudent().getId().equals(student.getId())) {
            throw new BookingException("Booking not found.");
        }
        if (s.getStatus() == SessionStatus.CANCELLED) {
            return;
        }
        Proctor hadProctor = s.getProctor();
        s.setStatus(SessionStatus.CANCELLED);
        s.setProctor(null);
        sessions.save(s);
        notifyStaff(student.getFullName() + " cancelled their test on " + fmt.inZone(s.getStartsAt(), s.getTimeZone())
            + (hadProctor == null ? "." : " (proctor was " + hadProctor.getFullName() + ")."));
    }

    /**
     * Allots a proctor, refusing one who is already busy at that time. No emails are sent; supervisors
     * see it in the portal's Activity list.
     */
    @Transactional
    public void assign(long sessionId, long proctorId) {
        ExamSession s = sessions.findById(sessionId)
            .orElseThrow(() -> new BookingException("Booking not found."));
        if (s.getStatus() == SessionStatus.CANCELLED) {
            throw new BookingException("That booking was cancelled by the student.");
        }
        Proctor p = proctors.findById(proctorId)
            .orElseThrow(() -> new BookingException("Proctor not found."));
        if (!p.isActive()) {
            throw new BookingException(p.getFullName() + " is not active.");
        }
        for (ExamSession other : sessions.findByProctorAndStatus(p, SessionStatus.ASSIGNED)) {
            if (!other.getId().equals(s.getId())
                    && other.getStartsAt().isBefore(s.getEndsAt()) && other.getEndsAt().isAfter(s.getStartsAt())) {
                throw new BookingException(p.getFullName() + " already has a test at that time ("
                    + other.getStudent().getFullName() + ", " + fmt.display(other.getStartsAt()) + ").");
            }
        }
        boolean changed = s.getProctor() != null && !s.getProctor().getId().equals(p.getId());
        if (s.getProctor() != null && !changed) {
            return;   // same proctor again: nothing to tell anyone
        }
        s.setProctor(p);
        s.setStatus(SessionStatus.ASSIGNED);
        sessions.save(s);

        AppUser student = s.getStudent();
        Instant start = s.getStartsAt();
        String portalWhen = fmt.displayPart(start, "EEE d MMM, HH:mm") + " " + fmt.displayAbbr(start);
        notifyStaff(p.getFullName() + (changed ? " is now proctoring " : " will proctor ")
            + student.getFullName() + " on " + portalWhen + ".");

    }

    /** Every active proctor, marked free or busy at the given booking's time. */
    public List<ProctorAvailability> availability(long sessionId) {
        ExamSession s = sessions.findById(sessionId)
            .orElseThrow(() -> new BookingException("Booking not found."));
        List<ProctorAvailability> out = new ArrayList<>();
        for (Proctor p : proctors.findByActiveTrueOrderByFullNameAsc()) {
            String busyWith = null;
            for (ExamSession other : sessions.findByProctorAndStatus(p, SessionStatus.ASSIGNED)) {
                if (!other.getId().equals(s.getId())
                        && other.getStartsAt().isBefore(s.getEndsAt()) && other.getEndsAt().isAfter(s.getStartsAt())) {
                    busyWith = other.getStudent().getFullName() + " at " + fmt.displayPart(other.getStartsAt(), "HH:mm");
                    break;
                }
            }
            out.add(new ProctorAvailability(p.getId(), p.getFullName(), busyWith == null, busyWith));
        }
        return out;
    }

    public List<ExamSession> forStudent(AppUser student) {
        return sessions.findByStudentOrderByStartsAtAsc(student);
    }

    public List<ExamSession> all() {
        return sessions.findAllByOrderByStartsAtAsc();
    }

    /** Bookings that start today in the portal's display zone, cancelled ones left out. */
    public List<ExamSession> today() {
        ZonedDateTime startOfDay = ZonedDateTime.now(fmt.displayZone()).toLocalDate().atStartOfDay(fmt.displayZone());
        return sessions.findByStartsAtBetweenAndStatusNotOrderByStartsAtAsc(
            startOfDay.toInstant(), startOfDay.plusDays(1).toInstant().minusMillis(1), SessionStatus.CANCELLED);
    }

    private List<AppUser> staff() {
        return users.findByRoleInAndEnabledTrue(List.of(Role.SUPERVISOR, Role.ADMIN));
    }

    private void notifyStaff(String message) {
        for (AppUser staff : staff()) {
            notifications.save(new Notification(staff, message));
        }
    }
}
