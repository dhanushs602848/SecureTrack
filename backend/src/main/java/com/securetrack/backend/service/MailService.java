package com.securetrack.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:unconfigured@example.com}")
    private String mailFrom;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendPasswordResetMail(String toEmail, String resetToken) {
        String resetUrl = "http://localhost:5173/reset-password?token=" + resetToken;
        String messageText = "You requested a password reset for SecureTrack.\n\n"
                + "Reset Token: " + resetToken + "\n"
                + "Reset Link: " + resetUrl + "\n\n"
                + "If you did not request this, please ignore this email. The token will expire in 30 minutes.";

        try {
            if ("unconfigured@example.com".equals(mailFrom)) {
                logger.warn("SMTP email credentials unconfigured. Token generated for {}: {}", toEmail, resetToken);
                return false;
            }
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(mailFrom);
            mailMessage.setTo(toEmail);
            mailMessage.setSubject("SecureTrack Password Reset");
            mailMessage.setText(messageText);

            mailSender.send(mailMessage);
            logger.info("Password reset email sent successfully to {}", toEmail);
            return true;
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
