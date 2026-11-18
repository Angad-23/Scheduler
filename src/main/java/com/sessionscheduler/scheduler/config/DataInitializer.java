package com.sessionscheduler.scheduler.config;

import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.Proctor;
import com.sessionscheduler.scheduler.model.Role;
import com.sessionscheduler.scheduler.repo.ProctorRepository;
import com.sessionscheduler.scheduler.repo.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on startup:
 *  - creates the first supervisor from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD if that account does not exist;
 *  - loads sample proctors when the proctors table is empty and app.seed-demo-proctors is true.
 */
@Component
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final List<String[]> DEMO_PROCTORS = List.of(
        new String[] {"Priya Menon", "priya.menon@example.com"},
        new String[] {"Rahul Verma", "rahul.verma@example.com"},
        new String[] {"Sara Thomas", "sara.thomas@example.com"});

    private final AppProperties props;
    private final UserRepository users;
    private final ProctorRepository proctors;
    private final PasswordEncoder encoder;

    public DataInitializer(AppProperties props, UserRepository users, ProctorRepository proctors,
                           PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.proctors = proctors;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSupervisor();
        if (props.seedDemoProctors() && proctors.count() == 0) {
            DEMO_PROCTORS.forEach(p -> proctors.save(new Proctor(p[0], p[1])));
            log.info("Loaded {} sample proctors", DEMO_PROCTORS.size());
        }
    }

    private void seedSupervisor() {
        AppProperties.Admin admin = props.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()
                || admin.password() == null || admin.password().isBlank()) {
            log.warn("No APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD set, so no supervisor account was created");
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        AppUser u = new AppUser();
        u.setFullName(admin.name() == null || admin.name().isBlank() ? "Supervisor" : admin.name());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(admin.password()));
        u.setRole(Role.SUPERVISOR);
        u.setEnabled(true);
        users.save(u);
        log.info("Created supervisor account {}", email);
    }
}
