import { Resend } from "resend";

const resend = process.env.RESEND_API_KEY
  ? new Resend(process.env.RESEND_API_KEY)
  : null;

export async function sendLoginCode(email: string, code: string) {
  // No API key set (e.g. local dev) — just log the code so you can log in.
  if (!resend) {
    console.log(`[dev] login code for ${email}: ${code}`);
    return;
  }

  await resend.emails.send({
  from: process.env.EMAIL_FROM ?? "sign-in@auth.limelightcreatives.org",
  to: email,
  subject: `Your Limelight Creatives sign-in code`,
  text: `Hi ${email},

Your sign-in code is ${code}.

This code expires in 10 minutes. Enter it on the sign-in page to finish logging in.

If you didn't request this, you can safely ignore this email.

Thanks,
The Limelight Creatives Team

(P.S. this is an unmonitored inbox and cannot receive messages! If you need to get in touch, reach out to us directly at inquiries@limelightcreatives.org)`,
});
}
