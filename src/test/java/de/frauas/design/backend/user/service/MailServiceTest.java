package de.frauas.design.backend.user.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MailService")
class MailServiceTest {

    @Mock
    JavaMailSender mailSender;

    MailService mailService;

    /** Real Thymeleaf engine resolving the actual templates/mail/*.html files, so rendering is genuinely exercised. */
    private TemplateEngine realTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(new SpringStandardDialect());
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    @BeforeEach
    void setUp() {
        mailService = new MailService(mailSender, realTemplateEngine());
    }

    @Nested
    @DisplayName("sendRegistrationCode")
    class SendRegistrationCode {

        @Test
        @DisplayName("does not send when mail is disabled, only logs")
        void mailDisabled_doesNotSend() {
            ReflectionTestUtils.setField(mailService, "mailEnabled", false);

            mailService.sendRegistrationCode("alice@example.com", "ABCD1234", 24);

            verify(mailSender, never()).createMimeMessage();
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("renders the registration-code template and sends it as HTML")
        void mailEnabled_rendersTemplateAndSends() throws Exception {
            ReflectionTestUtils.setField(mailService, "mailEnabled", true);
            ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@design.local");
            MimeMessage message = newMimeMessage();
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailService.sendRegistrationCode("alice@example.com", "ABCD1234", 24);

            verify(mailSender).send(message);
            assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("alice@example.com");
            assertThat(message.getSubject()).isEqualTo("DESIGN DaaS — Email Verification");
            assertThat(message.getContent().toString()).contains("ABCD1234").contains("24");
        }
    }

    @Nested
    @DisplayName("sendAccountDeletedEmail")
    class SendAccountDeletedEmail {

        @Test
        @DisplayName("does not send when mail is disabled, only logs")
        void mailDisabled_doesNotSend() {
            ReflectionTestUtils.setField(mailService, "mailEnabled", false);

            mailService.sendAccountDeletedEmail("alice@example.com", "Alice");

            verify(mailSender, never()).createMimeMessage();
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("renders the account-deleted template and sends it to the deleted account")
        void mailEnabled_rendersTemplateAndSends() throws Exception {
            ReflectionTestUtils.setField(mailService, "mailEnabled", true);
            ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@design.local");
            MimeMessage message = newMimeMessage();
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailService.sendAccountDeletedEmail("alice@example.com", "Alice");

            verify(mailSender).send(message);
            assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("alice@example.com");
            assertThat(message.getSubject()).isEqualTo("DESIGN DaaS — Account Deleted");
            assertThat(message.getContent().toString()).contains("Alice").contains("alice@example.com");
        }
    }

    @Nested
    @DisplayName("sendApplicationRequestEmail")
    class SendApplicationRequestEmail {

        @Test
        @DisplayName("does not send when mail is disabled, only logs")
        void mailDisabled_doesNotSend() {
            ReflectionTestUtils.setField(mailService, "mailEnabled", false);

            mailService.sendApplicationRequestEmail("alice@example.com", "Alice", "1", "some-app");

            verify(mailSender, never()).createMimeMessage();
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("renders the application-request template, reply-to's the requester, and sends it as HTML")
        void mailEnabled_rendersTemplateAndSends() throws Exception {
            ReflectionTestUtils.setField(mailService, "mailEnabled", true);
            ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@design.local");
            MimeMessage message = newMimeMessage();
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailService.sendApplicationRequestEmail("alice@example.com", "Alice", "1", "some-app");

            verify(mailSender).send(message);
            assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("noreply@design.local");
            assertThat(message.getReplyTo()).extracting(Object::toString).containsExactly("alice@example.com");
            assertThat(message.getContent().toString())
                    .contains("Alice")
                    .contains("alice@example.com")
                    .contains("some-app");
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("a template that cannot be rendered is reported as MailSendException")
        void templateError() {
            ReflectionTestUtils.setField(mailService, "mailEnabled", true);
            ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@design.local");
            when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
            TemplateEngine broken = org.mockito.Mockito.mock(TemplateEngine.class);
            when(broken.process(org.mockito.ArgumentMatchers.anyString(), any()))
                    .thenThrow(new org.thymeleaf.exceptions.TemplateProcessingException("bad template"));
            mailService = new MailService(mailSender, broken);
            ReflectionTestUtils.setField(mailService, "mailEnabled", true);
            ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@design.local");

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> mailService.sendRegistrationCode("alice@example.com", "ABCD1234", 24))
                    .isInstanceOf(org.springframework.mail.MailSendException.class);
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("an SMTP failure is passed on to the caller")
        void sendError() {
            ReflectionTestUtils.setField(mailService, "mailEnabled", true);
            ReflectionTestUtils.setField(mailService, "fromAddress", "noreply@design.local");
            MimeMessage message = newMimeMessage();
            when(mailSender.createMimeMessage()).thenReturn(message);
            org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
                    .when(mailSender)
                    .send(message);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> mailService.sendApplicationRequestEmail("alice@example.com", "Alice", "1", "app"))
                    .isInstanceOf(org.springframework.mail.MailSendException.class)
                    .hasMessage("smtp down");
        }
    }
}
