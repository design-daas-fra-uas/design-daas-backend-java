package de.frauas.design.backend.user.service;

import de.frauas.design.backend.shared.util.LogMasking;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.nio.charset.StandardCharsets;

/**
 * Sends transactional emails (e.g. registration code) when mail is enabled, rendering
 * their bodies from the HTML templates under {@code templates/mail/} via Thymeleaf.
 *
 * <p>Set {@code MAIL_ENABLED=true} and the MAIL_* env vars to activate.
 * When disabled, nothing is sent: the (masked) recipient is logged at INFO and, for local
 * testing without an SMTP server, the registration code at DEBUG level only.</p>
 *
 * <p>Methods run on the bounded executor from {@code AsyncConfig}; failures are logged there
 * (and here) because an {@code @Async void} method has no caller to report them to.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private static final String REGISTRATION_CODE_TEMPLATE = "mail/registration-code";
    private static final String APPLICATION_REQUEST_TEMPLATE = "mail/application-request";

    private final JavaMailSender mailSender;

    private final TemplateEngine templateEngine;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@design.local}")
    private String fromAddress;

    /**
     * Sends the one-time email-verification code using the {@code mail/registration-code}
     * template.
     *
     * @param toEmail the recipient's email address
     * @param registrationCode the one-time code to embed in the email
     * @param expiresInHours how many hours the code remains valid, shown to the recipient
     */
    @Async
    public void sendRegistrationCode(String toEmail, String registrationCode, int expiresInHours) {
        if (!mailEnabled) {
            log.info(
                    "Mail disabled — skipping registration email to {} (expiresInHours={})",
                    LogMasking.maskEmail(toEmail),
                    expiresInHours);
            log.debug(
                    "Mail disabled — registration code for {} is {}", LogMasking.maskEmail(toEmail), registrationCode);
            return;
        }
        Context context = new Context();
        context.setVariable("registrationCode", registrationCode);
        context.setVariable("expiresInHours", expiresInHours);
        send(toEmail, "DESIGN DaaS — Email Verification", REGISTRATION_CODE_TEMPLATE, context);
        log.info("Registration code sent to {}", LogMasking.maskEmail(toEmail));
    }

    /**
     * Sends an application-access request email to notify administrators, using the
     * {@code mail/application-request} template.
     *
     * @param userEmail   the requesting user's email
     * @param userName    the requesting user's display name
     * @param userId      the requesting user's ID (as string)
     * @param application the application name being requested
     */
    @Async
    public void sendApplicationRequestEmail(String userEmail, String userName, String userId, String application) {
        if (!mailEnabled) {
            log.info(
                    "Mail disabled — skipping application request email for user={} id={} application={}",
                    LogMasking.maskEmail(userEmail),
                    userId,
                    application);
            return;
        }
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("userId", userId);
        context.setVariable("userEmail", userEmail);
        context.setVariable("application", application);
        sendReplyTo(
                fromAddress,
                userEmail,
                "DESIGN DaaS — Application Access Request",
                APPLICATION_REQUEST_TEMPLATE,
                context);
        log.info(
                "Application request email sent for user {} requesting {}",
                LogMasking.maskEmail(userEmail),
                application);
    }

    /**
     * Renders {@code templateName} with {@code context} and sends it as an HTML email
     * from {@link #fromAddress} to {@code toEmail}.
     *
     * @param toEmail the recipient's email address
     * @param subject the email subject line
     * @param templateName the Thymeleaf template name (under {@code templates/}, no extension)
     * @param context the template variables
     * @throws MailSendException if the message could not be assembled
     */
    private void send(String toEmail, String subject, String templateName, Context context) {
        sendReplyTo(toEmail, null, subject, templateName, context);
    }

    /**
     * Renders {@code templateName} with {@code context} and sends it as an HTML email
     * from {@link #fromAddress} to {@code toEmail}, optionally setting a reply-to address.
     *
     * @param toEmail the recipient's email address
     * @param replyTo the reply-to address, or {@code null} to omit it
     * @param subject the email subject line
     * @param templateName the Thymeleaf template name (under {@code templates/}, no extension)
     * @param context the template variables
     * @throws MailSendException if the message could not be assembled
     */
    private void sendReplyTo(String toEmail, String replyTo, String subject, String templateName, Context context) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            if (replyTo != null) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(subject);
            helper.setText(templateEngine.process(templateName, context), true);
        } catch (MessagingException | TemplateEngineException e) {
            log.error(
                    "sendReplyTo — failed to build template={} to={} replyTo={}",
                    templateName,
                    LogMasking.maskEmail(toEmail),
                    LogMasking.maskEmail(replyTo),
                    e);
            throw new MailSendException("Failed to build email from template " + templateName, e);
        }
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error(
                    "sendReplyTo — failed to send template={} to={} replyTo={}",
                    templateName,
                    LogMasking.maskEmail(toEmail),
                    LogMasking.maskEmail(replyTo),
                    e);
            throw e;
        }
    }
}
