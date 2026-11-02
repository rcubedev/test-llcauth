CREATE TYPE "Role" AS ENUM ('ADMIN', 'STAFF', 'PARTICIPANT');

CREATE TABLE "users" (
        "id" TEXT NOT NULL,
        "email" TEXT NOT NULL,
        "firstName" TEXT,
        "lastName" TEXT,
        "role" "Role" NOT NULL DEFAULT 'PARTICIPANT',
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "users_email_key" ON "users"("email");

CREATE TABLE "verification_codes" (
        "id" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "codeHash" TEXT NOT NULL,
        "expiresAt" TIMESTAMP(3) NOT NULL,
        "usedAt" TIMESTAMP(3),
        "attempts" INTEGER NOT NULL DEFAULT 0,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "verification_codes_pkey" PRIMARY KEY ("id"),
        CONSTRAINT "verification_codes_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE INDEX "verification_codes_userId_idx" ON "verification_codes"("userId");

CREATE TABLE "rate_limit_events" (
        "id" TEXT NOT NULL,
        "ip" TEXT NOT NULL,
        "type" TEXT NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "rate_limit_events_pkey" PRIMARY KEY ("id")
);
CREATE INDEX "rate_limit_events_ip_type_createdAt_idx" ON "rate_limit_events"("ip", "type", "createdAt");

CREATE TABLE "invite_links" (
        "id" TEXT NOT NULL,
        "code" TEXT NOT NULL,
        "role" "Role" NOT NULL DEFAULT 'STAFF',
        "maxUses" INTEGER,
        "uses" INTEGER NOT NULL DEFAULT 0,
        "expiresAt" TIMESTAMP(3) NOT NULL,
        "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "invite_links_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "invite_links_code_key" ON "invite_links"("code");

CREATE TABLE "invite_redemptions" (
        "inviteId" TEXT NOT NULL,
        "userId" TEXT NOT NULL,
        "redeemedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT "invite_redemptions_pkey" PRIMARY KEY ("inviteId", "userId"),
        CONSTRAINT "invite_redemptions_inviteId_fkey" FOREIGN KEY ("inviteId") REFERENCES "invite_links"("id") ON DELETE CASCADE ON UPDATE CASCADE,
        CONSTRAINT "invite_redemptions_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE
);
