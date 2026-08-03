import { randomInt, createHash } from "node:crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

export const OTP_TTL_MS = 10 * 60 * 1000;
export const MAX_ATTEMPTS = 5;
const OTP_CODE_DIGITS = 6;

export type OtpRecord = {
  hash: string;
  expiresAt: Timestamp;
  attempts: number;
  createdAt: Timestamp;
};

export type RequestEmailOtpRequest = { email: string };
export type VerifyEmailOtpRequest = { email: string; code: string };
export type VerifyEmailOtpResult =
  | { customToken: string }
  | { alreadyExists: true; customToken: string; userId: string };

export function generateOtpCode(): string {
  return randomInt(0, 10 ** OTP_CODE_DIGITS).toString().padStart(OTP_CODE_DIGITS, "0");
}

export function hashOtpCode(email: string, code: string): string {
  return createHash("sha256").update(`${email.toLowerCase()}:${code}`).digest("hex");
}

export function isOtpExpired(record: Pick<OtpRecord, "expiresAt">, now: number = Date.now()): boolean {
  return record.expiresAt.toMillis() <= now;
}

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function otpDocRef(email: string) {
  return getFirestore().collection("otpCodes").doc(normalizeEmail(email));
}

export function buildOtpMailMessage(email: string, code: string) {
  return {
    to: [email],
    message: {
      subject: "Your Basil verification code",
      text: `Your verification code is ${code}. It expires in 10 minutes.`,
      html: `<p>Your verification code is <strong>${code}</strong>.</p><p>It expires in 10 minutes.</p>`,
    },
  };
}

export async function requestOtpForEmail(email: string): Promise<void> {
  const code = generateOtpCode();
  const hash = hashOtpCode(email, code);
  const now = Timestamp.now();
  const expiresAt = Timestamp.fromMillis(now.toMillis() + OTP_TTL_MS);

  const db = getFirestore();
  await db.runTransaction(async (tx) => {
    tx.set(otpDocRef(email), {
      hash,
      expiresAt,
      attempts: 0,
      createdAt: now,
    } satisfies OtpRecord);
    tx.set(db.collection("mail").doc(), buildOtpMailMessage(email, code));
  });
}

export const requestEmailOtp = onCall<RequestEmailOtpRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }
  const email = request.data?.email;
  if (!email || typeof email !== "string") {
    throw new HttpsError("invalid-argument", "A valid email is required");
  }

  await requestOtpForEmail(email);
  return { sent: true };
});

export async function verifyOtpForEmail(
  email: string,
  code: string,
  callerUid: string,
  callerIsAnonymous: boolean,
): Promise<VerifyEmailOtpResult> {
  if (!code || typeof code !== "string") {
    throw new HttpsError("invalid-argument", "A verification code is required");
  }

  const ref = otpDocRef(email);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("failed-precondition", "No verification code was requested for this email");
  }
  const record = snap.data() as OtpRecord;

  if (record.attempts >= MAX_ATTEMPTS) {
    throw new HttpsError("resource-exhausted", "Too many attempts, request a new code");
  }
  if (isOtpExpired(record)) {
    throw new HttpsError("deadline-exceeded", "Verification code expired");
  }

  const expectedHash = hashOtpCode(email, code);
  if (expectedHash !== record.hash) {
    await ref.update({ attempts: record.attempts + 1 });
    throw new HttpsError("invalid-argument", "Incorrect verification code");
  }

  await ref.delete();

  const auth = getAuth();
  const normalizedEmail = normalizeEmail(email);

  let existingUser;
  try {
    existingUser = await auth.getUserByEmail(normalizedEmail);
  } catch (e) {
    existingUser = null;
  }

  if (!existingUser) {
    if (!callerIsAnonymous) {
      throw new HttpsError("failed-precondition", "Caller must be anonymous to claim a new email");
    }
    await auth.updateUser(callerUid, { email: normalizedEmail, emailVerified: true });
    const customToken = await auth.createCustomToken(callerUid);
    return { customToken };
  }

  if (existingUser.uid === callerUid) {
    if (!existingUser.emailVerified) {
      await auth.updateUser(callerUid, { emailVerified: true });
    }
    const customToken = await auth.createCustomToken(callerUid);
    return { customToken };
  }

  const customToken = await auth.createCustomToken(existingUser.uid);
  return { alreadyExists: true, customToken, userId: existingUser.uid };
}

export const verifyEmailOtp = onCall<VerifyEmailOtpRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }
  const { email, code } = request.data ?? {};
  if (!email || typeof email !== "string") {
    throw new HttpsError("invalid-argument", "A valid email is required");
  }

  const callerIsAnonymous = request.auth.token.firebase?.sign_in_provider === "anonymous";
  return verifyOtpForEmail(email, code, request.auth.uid, callerIsAnonymous);
});
