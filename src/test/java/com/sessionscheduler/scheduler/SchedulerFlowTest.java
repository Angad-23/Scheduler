package com.sessionscheduler.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.ExamSession;
import com.sessionscheduler.scheduler.model.Proctor;
import com.sessionscheduler.scheduler.model.SessionStatus;
import com.sessionscheduler.scheduler.repo.ExamSessionRepository;
import com.sessionscheduler.scheduler.repo.NotificationRepository;
import com.sessionscheduler.scheduler.repo.ProctorRepository;
import com.sessionscheduler.scheduler.service.AccountException;
import com.sessionscheduler.scheduler.service.AccountService;
import com.sessionscheduler.scheduler.service.BookingException;
import com.sessionscheduler.scheduler.service.BookingService;
import com.sessionscheduler.scheduler.service.ExcelExportService;
import com.sessionscheduler.scheduler.service.MailMessage;
import com.sessionscheduler.scheduler.service.MailService;
import com.sessionscheduler.scheduler.service.ProctorAvailability;
import com.sessionscheduler.scheduler.service.TimeZones;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Runs the whole flow against an in-memory database: register, verify, book, assign, export. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:scheduler;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.admin.email=supervisor@example.com",
    "app.admin.password=ChangeMe123!"
})
class SchedulerFlowTest {

    @MockitoBean JavaMailSender mailSender;

    @Autowired AccountService accounts;
    @Autowired BookingService bookings;
    @Autowired ExcelExportService excel;
    @Autowired ProctorRepository proctors;
    @Autowired ExamSessionRepository sessions;
    @Autowired NotificationRepository notifications;
    @Autowired MailService mail;

    @Test
    void studentRegistersBooksAndSupervisorAssigns() {
        String token = accounts.registerStudent("Asha Rao", "Asha@Example.com", "password123");
        assertThat(accounts.requireByEmail("asha@example.com").isEnabled()).isFalse();

        assertThat(accounts.verify("not-a-token")).isFalse();
        assertThat(accounts.verify(token)).isTrue();
        AppUser student = accounts.requireByEmail("asha@example.com");
        assertThat(student.isEnabled()).isTrue();

        LocalDate date = LocalDate.now().plusDays(3);
        // only US zones are accepted
        assertThatThrownBy(() -> bookings.book(student, date, LocalTime.of(10, 0), "Asia/Kolkata"))
            .isInstanceOf(BookingException.class);
        ExamSession s = bookings.book(student, date, LocalTime.of(10, 0), "America/New_York");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.REQUESTED);

        AppUser supervisor = accounts.requireByEmail("supervisor@example.com");
        assertThat(notifications.countByRecipientAndReadFlagFalse(supervisor)).isEqualTo(1);

        // the same student cannot double-book an overlapping time
        assertThatThrownBy(() ->
            bookings.book(student, date, LocalTime.of(10, 30), "America/New_York"))
            .isInstanceOf(BookingException.class);

        Proctor proctor = proctors.save(new Proctor("Vikram Shah", "vikram@example.com"));
        bookings.assign(s.getId(), proctor.getId());
        ExamSession assigned = sessions.findById(s.getId()).orElseThrow();
        assertThat(assigned.getStatus()).isEqualTo(SessionStatus.ASSIGNED);
        assertThat(assigned.getProctor().getId()).isEqualTo(proctor.getId());

        // a second session at the same time shows that proctor as busy
        String token2 = accounts.registerStudent("Ben Lee", "ben@example.com", "password123");
        accounts.verify(token2);
        // 09:30 Central is 10:30 Eastern, inside Asha's hour
        ExamSession clash = bookings.book(accounts.requireByEmail("ben@example.com"), date,
            LocalTime.of(9, 30), "America/Chicago");
        List<ProctorAvailability> avail = bookings.availability(clash.getId());
        assertThat(avail).anyMatch(a -> a.id().equals(proctor.getId()) && !a.available());
        assertThatThrownBy(() -> bookings.assign(clash.getId(), proctor.getId())).isInstanceOf(BookingException.class);

        assertThat(bookings.all()).hasSize(2);
        assertThat(excel.export(bookings.all()).length).isGreaterThan(1000);
    }

    @Test
    void loggedInUserChangesPassword() {
        String token = accounts.registerStudent("Cara Diaz", "cara@example.com", "firstpass1");
        accounts.verify(token);
        assertThatThrownBy(() -> accounts.changePassword("cara@example.com", "wrongpass", "secondpass2"))
            .isInstanceOf(AccountException.class);
        accounts.changePassword("cara@example.com", "firstpass1", "secondpass2");
        assertThatThrownBy(() -> accounts.changePassword("cara@example.com", "firstpass1", "thirdpass3"))
            .isInstanceOf(AccountException.class);
    }

    @Test
    void timeZonesAreGroupedByRegion() {
        assertThat(TimeZones.regions()).contains("Asia", "Europe", "America", "UTC");
        assertThat(TimeZones.zones("Asia")).contains("Asia/Kolkata");
        assertThat(TimeZones.regionOf("Asia/Kolkata")).isEqualTo("Asia");
    }

    @Test
    void emailTemplateRenders() {
        MailMessage m = MailMessage.to("a@example.com")
            .subject("Subject").preheader("Preview").heading("New session request")
            .status("Awaiting proctor", MailMessage.Tone.WAITING)
            .paragraph("Body text").detail("Exam", "SSAT").detail("Notes", " ")
            .button("Assign a proctor", "http://localhost:8080/admin").footnote("Footnote");
        String html = mail.render(m);
        assertThat(html).contains("New session request", "SSAT", "Assign a proctor", "http://localhost:8080/admin", "#fdf3e2");
        assertThat(html).doesNotContain("Angad Dhiman", "th:", "MyVirtualTAs");   // sample rows are removed
        assertThat(m.toPlainText("Trivium Scheduler")).contains("Exam: SSAT", "Trivium Scheduler").doesNotContain("Notes:");
    }

    @Test
    void supervisorIsCreatedByHandAndCanBeDeactivated() {
        AppUser boss = accounts.requireByEmail("supervisor@example.com");
        assertThatThrownBy(() -> accounts.createSupervisor("Ravi Kumar", "ravi@example.com", "short"))
            .isInstanceOf(AccountException.class);
        accounts.createSupervisor("Ravi Kumar", "Ravi@Example.com", "ravipass123");
        AppUser ravi = accounts.requireByEmail("ravi@example.com");
        assertThat(ravi.isEnabled()).isTrue();
        assertThat(accounts.staffState(ravi)).isEqualTo("active");

        // the same address can't become a student or a second supervisor
        assertThatThrownBy(() -> accounts.registerStudent("Ravi", "ravi@example.com", "password123"))
            .isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> accounts.createSupervisor("Ravi", "ravi@example.com", "password123"))
            .isInstanceOf(AccountException.class);

        accounts.setSupervisorPassword(ravi.getId(), "newravipass1");
        accounts.changePassword("ravi@example.com", "newravipass1", "ravipass456");

        assertThatThrownBy(() -> accounts.setStaffActive(boss.getId(), false, boss)).isInstanceOf(AccountException.class);
        accounts.setStaffActive(ravi.getId(), false, boss);
        assertThat(accounts.staffState(accounts.requireByEmail("ravi@example.com"))).isEqualTo("deactivated");
        assertThat(accounts.staff()).extracting(AppUser::getEmail).contains("supervisor@example.com", "ravi@example.com");
    }

    @Test
    void usTimeZonesShowShortNames() {
        java.time.Instant winter = java.time.ZonedDateTime.of(2026, 12, 1, 10, 0, 0, 0, java.time.ZoneId.of("America/New_York")).toInstant();
        java.time.Instant summer = java.time.ZonedDateTime.of(2026, 7, 1, 10, 0, 0, 0, java.time.ZoneId.of("America/Los_Angeles")).toInstant();
        com.sessionscheduler.scheduler.service.TimeFormat fmt = new com.sessionscheduler.scheduler.service.TimeFormat(
            new com.sessionscheduler.scheduler.config.AppProperties("Trivium Scheduler", "http://x", "Asia/Kolkata", "a@b.c", "n", true, "smtp", "", 24, false, 60, null));
        assertThat(fmt.abbr(winter, "America/New_York")).isEqualTo("EST");
        assertThat(fmt.abbr(summer, "America/Los_Angeles")).isEqualTo("PDT");
        assertThat(com.sessionscheduler.scheduler.service.UsTimeZones.isAllowed("America/Chicago")).isTrue();
        assertThat(com.sessionscheduler.scheduler.service.UsTimeZones.isAllowed("Asia/Kolkata")).isFalse();
    }
}
