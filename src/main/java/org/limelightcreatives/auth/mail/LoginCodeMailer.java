package org.limelightcreatives.auth.mail;

/** Delivers a sign-in code to someone. */
public interface LoginCodeMailer {

    void send(String email, String code);

    static String body(String email, String code) {
        return """
                Hi %s,

                Your sign-in code is %s.

                This code expires in 10 minutes. Enter it on the sign-in page to finish logging in.

                If you didn't request this, you can safely ignore this email.

                Thanks,
                The Limelight Creatives Team

                (P.S. this is an unmonitored inbox and cannot receive messages! If you need to get in touch, \
                reach out to us directly at inquiries@limelightcreatives.org)""".formatted(email, code);
    }

    String SUBJECT = "Your Limelight Creatives sign-in code";
}
