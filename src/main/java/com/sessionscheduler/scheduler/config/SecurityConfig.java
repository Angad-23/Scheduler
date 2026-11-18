package com.sessionscheduler.scheduler.config;

import com.sessionscheduler.scheduler.repo.UserRepository;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Form login on a custom /login page, accounts in the database, BCrypt passwords,
 * CSRF on every POST (Thymeleaf adds the hidden _csrf field to each th:action form), logout by POST.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // public: landing, sign-up, email verification, login, static files, error page
                .requestMatchers("/", "/register", "/verify", "/login", "/error", "/forgot-password", "/set-password",
                    "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/admin/**", "/api/proctors/**").hasAnyRole("SUPERVISOR", "ADMIN")
                // everything else, including /account/password and /api/timezones, needs a login
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .successHandler(roleBasedRedirect())
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")                 // POST only, with the CSRF token
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("SESSION", "JSESSIONID")
                .permitAll());
        return http.build();
    }

    /** Students land on the booking page, supervisors and admins on the portal. */
    private AuthenticationSuccessHandler roleBasedRedirect() {
        return (request, response, authentication) -> {
            boolean student = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
            response.sendRedirect(request.getContextPath() + (student ? "/student" : "/admin"));
        };
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
            .map(u -> User.withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .roles(u.getRole().name())
                .disabled(!u.isEnabled())     // unverified accounts cannot log in
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
