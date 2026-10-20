import { NextResponse } from "next/server";
import { Prisma } from "@prisma/client";
import { auth } from "@/auth";
import { prisma } from "@/lib/prisma";

const FAIL_WINDOW_MS = 60 * 60 * 1000;
// Only wrong guesses count, so a room of mentors on one venue wifi isn't throttled.
const MAX_BAD_CODES_PER_IP = 20;

const INACTIVE = "This invite link is no longer active. Ask an organiser for help.";

function getClientIp(req: Request): string {
  const forwarded = req.headers.get("x-forwarded-for");
  return forwarded?.split(",")[0]?.trim() ?? "unknown";
}

export async function POST(req: Request) {
  // Signing in with the emailed code has already proved they own the address.
  const session = await auth();
  const userId = (session?.user as { id?: string } | undefined)?.id;
  if (!userId) {
    return NextResponse.json({ error: "Not signed in" }, { status: 401 });
  }

  const body = await req.json().catch(() => ({}));
  const code = typeof body?.code === "string" ? body.code.trim().toLowerCase() : "";
  if (!code || code.length > 64) {
    return NextResponse.json({ error: INACTIVE }, { status: 400 });
  }

  const ip = getClientIp(req);
  const badCodes = await prisma.rateLimitEvent.count({
    where: { ip, type: "invite_fail", createdAt: { gt: new Date(Date.now() - FAIL_WINDOW_MS) } },
  });
  if (badCodes >= MAX_BAD_CODES_PER_IP) {
    return NextResponse.json({ error: "Too many attempts. Please try again later." }, { status: 429 });
  }

  try {
    const result = await prisma.$transaction(async (tx) => {
      const invite = await tx.inviteLink.findUnique({ where: { code } });
      // Never let a shared link create admins, whatever the row says.
      if (!invite || invite.role === "ADMIN") return "unknown" as const;

      const already = await tx.inviteRedemption.findUnique({
        where: { inviteId_userId: { inviteId: invite.id, userId } },
      });

      if (!already) {
        // Atomic: the expiry and cap are checked in the same UPDATE that takes the slot,
        // so two people grabbing the last slot can't both succeed.
        const claimed = await tx.inviteLink.updateMany({
          where: {
            id: invite.id,
            expiresAt: { gt: new Date() },
            ...(invite.maxUses !== null ? { uses: { lt: invite.maxUses } } : {}),
          },
          data: { uses: { increment: 1 } },
        });
        if (claimed.count === 0) return "closed" as const;
        await tx.inviteRedemption.create({ data: { inviteId: invite.id, userId } });
      }

      // Only ever upgrade a plain participant, never downgrade staff or admins.
      await tx.user.updateMany({
        where: { id: userId, role: "PARTICIPANT" },
        data: { role: invite.role },
      });
      return "ok" as const;
    });

    if (result === "unknown") {
      await prisma.rateLimitEvent.create({ data: { ip, type: "invite_fail" } });
      return NextResponse.json({ error: INACTIVE }, { status: 400 });
    }
    if (result === "closed") {
      return NextResponse.json({ error: INACTIVE }, { status: 400 });
    }
    return NextResponse.json({ ok: true });
  } catch (e) {
    // Same person redeemed twice at the same instant: the other request won, which is fine.
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === "P2002") {
      return NextResponse.json({ ok: true });
    }
    return NextResponse.json({ error: "Something went wrong. Try again." }, { status: 500 });
  }
}