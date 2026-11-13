package org.limelightcreatives.auth.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How login codes are emailed under {@code auth.mail:}.
 *
 * @param resendApiKey the Resend API key. Without one, codes are only written to the log
 * @param from the sender address. Must be on a domain that is verified in Resend
 * @param logCodeIfUnsent when an email can't be sent, write its code to the log instead so you can still
 *                        sign in while testing. Used in local development only and never in production.
 * @param logCodesWithoutKey without an API key write every code to the log instead of refusing to start.
 *                           Used in local development and tests only.
 */
@ConfigurationProperties("auth.mail")
public record MailProperties(String resendApiKey, String from, boolean logCodeIfUnsent, boolean logCodesWithoutKey) {
}
