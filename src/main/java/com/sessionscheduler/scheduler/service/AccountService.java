package com.sessionscheduler.scheduler.service;

import com.sessionscheduler.scheduler.config.AppProperties;
import com.sessionscheduler.scheduler.model.AppUser;
import com.sessionscheduler.scheduler.model.Role;
import com.sessionscheduler.scheduler.model.VerificationToken;
import com.sessionscheduler.scheduler.repo.UserRepository;
import com.sessionscheduler.scheduler.repo.VerificationTokenRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final VerificationTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository users, VerificationTokenRepository tokens, PasswordEncoder encoder,
                          MailService mail, AppProperties props) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mail = mail;
        this.props = props;
    }

    static String firstName(String fullName) {
        String n = fullName == null ? "" : fullName.trim();
        int space = n.indexOf(' ');
        return space > 0 ? n.substring(0, space) : (n.isEmpty() ? "there" : n);
    }

    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Registers a student (or refreshes an unverified registration) and emails a verification link.
     * Returns the token so tests can verify without reading the email.
     */
    @Transactional
    public String registerStudent(String fullName, String rawEmail, String password) {
        String email = normalize(rawEmail);
        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent() && existing.get().getRole() != Role.STUDENT) {
            throw new AccountException("This email belongs to a supervisor account. Sign in, or use Forgot password.");
        }
        if (existing.isPresent() && existing.get().isEnabled()) {
            throw new AccountException("This email is already registered. Sign in instead.");
        }
        AppUser user = existing.orElseGet(AppUser::new);
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(Role.STUDENT);
        user.setEnabled(false);
        user = users.save(user);

        tokens.deleteByUser(user);
        tokens.flush();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        int hours = props.verificationHours() > 0 ? props.verificationHours() : 24;
        tokens.save(new VerificationToken(token, user, Instant.now().plus(Duration.ofHours(hours))));

        String link = props.baseUrl() + "/verify?token=" + token;
        mail.send(MailMessage.to(email)
            .subject("Confirm your email for " + props.brandNameOrDefault())
            .preheader("One click to activate your account and start booking proctored sessions.")
            .heading("Confirm your email")
            .greeting("Hi " + firstName(user.getFullName()) + ",")
            .paragraph("Thanks for registering with " + props.brandNameOrDefault() + ". Confirm your email address "
                + "to activate your account, then sign in to request proctored sessions.")
            .button("Confirm email", link)
            .footnote("This link expires in " + hours + " hours.")
            .footnote("Button not working? Copy this link into your browser: " + link)
            .reason("You're receiving this email because someone registered this address with " + props.brandNameOrDefault() + ". "
                + "If that wasn't you, ignore this email and no account will be created."));
        return token;
    }

    /** Activates the account behind a token. Returns false when the token is unknown or expired. */
    @Transactional
    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<VerificationToken> found = tokens.findByToken(token.trim());
        if (found.isEmpty()) {
            return false;
        }
        VerificationToken vt = found.get();
        boolean valid = vt.getExpiresAt().isAfter(Instant.now());
        if (valid) {
            vt.getUser().setEnabled(true);
            users.save(vt.getUser());
        }
        tokens.delete(vt);
        return valid;
    }

    /** Changes a logged-in user's password after checking the current one. */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        AppUser user = requireByEmail(email);
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AccountException("Your current password is not correct.");
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new AccountException("Choose a new password that is different from the current one.");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
    }

    // ---------- supervisors: created by hand with a login and password ----------

    private static final Duration RESET_VALID = Duration.ofHours(1);

    /** Creates an active supervisor account with the password you choose. No email is sent. */
    @Transactional
    public void createSupervisor(String fullName, String rawEmail, String password) {
        String email = normalize(rawEmail);
        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            throw new AccountException(existing.get().getRole() == Role.STUDENT
                ? email + " is already registered as a student."
                : email + " already has a supervisor account.");
        }
        checkPassword(password);
        AppUser user = new AppUser();
        user.setFullName(fullName.trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(Role.SUPERVISOR);
        user.setEnabled(true);
        users.save(user);
    }

    /** Sets a new password for a supervisor (when they've forgotten it). */
    @Transactional
    public void setSupervisorPassword(long userId, String password) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AccountException("Account not found."));
        if (user.getRole() == Role.STUDENT) {
            throw new AccountException("That account isn't a supervisor.");
        }
        checkPassword(password);
        user.setPasswordHash(encoder.encode(password));
        users.save(user);
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new AccountException("Use a password of 8 to 72 characters.");
        }
    }

    public List<AppUser> staff() {
        return users.findByRoleInOrderByFullNameAsc(List.of(Role.SUPERVISOR, Role.ADMIN));
    }

    /** "active" or "deactivated", for the Supervisors page. */
    public String staffState(AppUser user) {
        return user.isEnabled() ? "active" : "deactivated";
    }

    @Transactional
    public void setStaffActive(long userId, boolean active, AppUser actingUser) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AccountException("Account not found."));
        if (user.getRole() == Role.STUDENT) {
            throw new AccountException("That account isn't a supervisor.");
        }
        if (!active && user.getId().equals(actingUser.getId())) {
            throw new AccountException("You can't deactivate your own account.");
        }
        user.setEnabled(active);
        users.save(user);
        if (!active) {
            tokens.deleteByUser(user);   // any password-reset link stops working too
        }
    }

    // ---------- forgot password / set password by link ----------

    /**
     * Emails a reset link if an active account has this address. Says nothing either way, so the
     * page can't be used to find out who has an account.
     */
    @Transactional
    public void startPasswordReset(String rawEmail) {
        users.findByEmail(normalize(rawEmail)).filter(AppUser::isEnabled).ifPresent(user -> {
            String token = issueToken(user, RESET_VALID);
            String link = props.baseUrl() + "/set-password?token=" + token;
            mail.send(MailMessage.to(user.getEmail())
                .subject("Reset your " + props.brandNameOrDefault() + " password")
                .preheader("Use this link within the hour to choose a new password.")
                .heading("Reset your password")
                .greeting("Hi " + firstName(user.getFullName()) + ",")
                .paragraph("We received a request to reset the password for your " + props.brandNameOrDefault() + " account. "
                    + "Choose a new one with the button below.")
                .button("Choose a new password", link)
                .footnote("This link expires in 1 hour. If you didn't ask for this, ignore this email; your password stays the same.")
                .footnote("Button not working? Copy this link into your browser: " + link));
        });
    }

    /** The account a set-password link belongs to, if the link is still valid. */
    public Optional<AppUser> userForToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByToken(token.trim())
            .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
            .map(VerificationToken::getUser);
    }

    /** Sets the password from a reset link and uses up the link. */
    @Transactional
    public void setPasswordWithToken(String token, String newPassword) {
        VerificationToken vt = tokens.findByToken(token == null ? "" : token.trim())
            .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new AccountException("This link has expired or was already used."));
        AppUser user = vt.getUser();
        user.setPasswordHash(encoder.encode(newPassword));
        if (user.getRole() == Role.STUDENT) {
            user.setEnabled(true);   // following an emailed link also proves the student owns the address
        }
        users.save(user);
        tokens.deleteByUser(user);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Replaces any earlier link for this user with a fresh one. */
    private String issueToken(AppUser user, Duration validFor) {
        tokens.deleteByUser(user);
        tokens.flush();
        String token = newToken();
        tokens.save(new VerificationToken(token, user, Instant.now().plus(validFor)));
        return token;
    }

    public AppUser requireByEmail(String email) {
        return users.findByEmail(normalize(email))
            .orElseThrow(() -> new AccountException("Account not found."));
    }
}
