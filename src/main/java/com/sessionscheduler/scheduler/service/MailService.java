package com.sessionscheduler.scheduler.service;

import com.sessionscheduler.scheduler.config.AppProperties;
import com.sessionscheduler.scheduler.config.Brand;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders emails with the branded HTML layout (templates/mail/message.html) and sends them in the
 * background, with a plain-text copy, so a slow or broken mail server never blocks a booking.
 * In local mode (app.mail-log-only) nothing is sent: the text goes to the console and the HTML is
 * saved under data/mail-preview so you can open it in a browser.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final JavaMailSender sender;
    private final AppProperties props;
    private final ITemplateEngine templates;
    private final Brand brand;
    private final RestClient brevo;

    public MailService(JavaMailSender sender, AppProperties props, ITemplateEngine templates, Brand brand) {
        this.sender = sender;
        this.props = props;
        this.templates = templates;
        this.brand = brand;
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(10_000);
        http.setReadTimeout(20_000);
        this.brevo = RestClient.builder().baseUrl("https://api.brevo.com").requestFactory(http).build();
    }

    /** Renders the email to HTML. Public so it can be previewed and tested. */
    public String render(MailMessage m) {
        Context ctx = new Context();
        ctx.setVariable("m", m);
        ctx.setVariable("baseUrl", props.baseUrl());
        ctx.setVariable("year", Year.now().getValue());
        ctx.setVariable("brand", brand.getName());
        return templates.process("mail/message", ctx);
    }

    @Async
    public void send(MailMessage m) {
        String html;
        try {
            html = render(m);
        } catch (Exception e) {
            log.error("Could not build the email \"{}\" for {}", m.getSubject(), m.getTo(), e);
            return;
        }
        String text = m.toPlainText(brand.getName());

        if (props.mailLogOnly()) {
            Path file = savePreview(m, html);
            log.info("\n===== EMAIL (not sent) =====\nTo: {}\nSubject: {}\nHTML preview: {}\n\n{}============================",
                m.getTo(), m.getSubject(), file == null ? "(not saved)" : file.toAbsolutePath(), text);
            return;
        }
        if (props.usesBrevo()) {
            sendWithBrevo(m, html, text);
            return;
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(props.mailFrom(), senderName());
            helper.setTo(m.getTo());
            helper.setSubject(m.getSubject());
            helper.setText(text, html);
            sender.send(mime);
        } catch (Exception e) {
            log.warn("Could not send email to {} ({}): {}", m.getTo(), m.getSubject(), e.getMessage());
        }
    }

    /** Brevo transactional email API (HTTPS, port 443). The sender address must be verified in Brevo. */
    private void sendWithBrevo(MailMessage m, String html, String text) {
        if (props.brevoApiKey() == null || props.brevoApiKey().isBlank()) {
            log.warn("APP_MAIL_PROVIDER is brevo but BREVO_API_KEY is empty, so the email to {} was not sent", m.getTo());
            return;
        }
        Map<String, Object> body = Map.of(
            "sender", Map.of("name", senderName(), "email", props.mailFrom()),
            "to", List.of(Map.of("email", m.getTo())),
            "subject", m.getSubject(),
            "htmlContent", html,
            "textContent", text);
        try {
            brevo.post().uri("/v3/smtp/email")
                .header("api-key", props.brevoApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("Brevo refused the email to {} ({}): HTTP {} {}", m.getTo(), m.getSubject(),
                e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Could not reach Brevo for the email to {} ({}): {}", m.getTo(), m.getSubject(), e.getMessage());
        }
    }

    private String senderName() {
        String name = props.mailFromName();
        return name == null || name.isBlank() ? brand.getName() : name;
    }

    private Path savePreview(MailMessage m, String html) {
        try {
            Path dir = Path.of("data", "mail-preview");
            Files.createDirectories(dir);
            String who = m.getTo().replaceAll("[^A-Za-z0-9._-]", "_");
            Path file = dir.resolve(LocalDateTime.now().format(FILE_STAMP) + "-" + who + ".html");
            Files.writeString(file, html, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            log.debug("Could not save email preview: {}", e.getMessage());
            return null;
        }
    }
}
