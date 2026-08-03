import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockDb, resetFirestoreMock } = vi.hoisted(() => {
  const store = new Map<string, any>();
  let autoIdCounter = 0;

  function makeDocRef(path: string) {
    return {
      id: path.split("/").pop()!,
      get: async () => {
        const data = store.get(path);
        return { exists: data !== undefined, data: () => data };
      },
      set: async (data: any) => {
        store.set(path, data);
      },
      update: async (data: any) => {
        store.set(path, { ...store.get(path), ...data });
      },
      delete: async () => {
        store.delete(path);
      },
    };
  }

  function makeCollectionRef(name: string) {
    return {
      doc: (id?: string) => makeDocRef(`${name}/${id ?? `auto-${autoIdCounter++}`}`),
    };
  }

  const mockDb = {
    collection: (name: string) => makeCollectionRef(name),
    runTransaction: async (fn: (tx: any) => Promise<void> | void) => {
      const tx = {
        set: (ref: any, data: any) => ref.set(data),
        update: (ref: any, data: any) => ref.update(data),
        delete: (ref: any) => ref.delete(),
      };
      return fn(tx);
    },
  };

  return {
    mockDb,
    resetFirestoreMock: () => {
      store.clear();
      autoIdCounter = 0;
    },
  };
});

const { mockAuth, resetAuthMock } = vi.hoisted(() => {
  const usersByUid = new Map<string, { uid: string; email?: string; emailVerified?: boolean }>();

  const mockAuth = {
    getUserByEmail: async (email: string) => {
      const found = [...usersByUid.values()].find((u) => u.email === email);
      if (!found) {
        const err: any = new Error("no user record");
        err.code = "auth/user-not-found";
        throw err;
      }
      return found;
    },
    updateUser: async (uid: string, data: Record<string, unknown>) => {
      const existing = usersByUid.get(uid) ?? { uid };
      const updated = { ...existing, ...data };
      usersByUid.set(uid, updated);
      return updated;
    },
    createCustomToken: async (uid: string) => `custom-token-${uid}`,
    _seedUser: (user: { uid: string; email?: string; emailVerified?: boolean }) => {
      usersByUid.set(user.uid, user);
    },
  };

  return {
    mockAuth,
    resetAuthMock: () => usersByUid.clear(),
  };
});

vi.mock("firebase-admin/firestore", async () => {
  const actual = await vi.importActual<typeof import("firebase-admin/firestore")>("firebase-admin/firestore");
  return { ...actual, getFirestore: () => mockDb };
});

vi.mock("firebase-admin/auth", async () => {
  const actual = await vi.importActual<typeof import("firebase-admin/auth")>("firebase-admin/auth");
  return { ...actual, getAuth: () => mockAuth };
});

import { Timestamp } from "firebase-admin/firestore";
import {
  MAX_ATTEMPTS,
  OTP_TTL_MS,
  buildOtpMailMessage,
  generateOtpCode,
  hashOtpCode,
  isOtpExpired,
  requestOtpForEmail,
  verifyOtpForEmail,
} from "./emailOtp";

beforeEach(() => {
  resetFirestoreMock();
  resetAuthMock();
});

describe("generateOtpCode", () => {
  it("always generates a 6-digit zero-padded code", () => {
    for (let i = 0; i < 50; i++) {
      expect(generateOtpCode()).toMatch(/^\d{6}$/);
    }
  });
});

describe("hashOtpCode", () => {
  it("is deterministic and case-insensitive on the email", () => {
    const a = hashOtpCode("User@Example.com", "123456");
    const b = hashOtpCode("user@example.com", "123456");
    expect(a).toBe(b);
  });

  it("differs for different codes", () => {
    expect(hashOtpCode("user@example.com", "123456")).not.toBe(hashOtpCode("user@example.com", "654321"));
  });
});

describe("isOtpExpired", () => {
  it("treats future expiry as not expired and past expiry as expired", () => {
    const future = Timestamp.fromMillis(Date.now() + 60_000);
    const past = Timestamp.fromMillis(Date.now() - 60_000);
    expect(isOtpExpired({ expiresAt: future })).toBe(false);
    expect(isOtpExpired({ expiresAt: past })).toBe(true);
  });
});

describe("buildOtpMailMessage", () => {
  it("shapes the document for the Trigger Email extension", () => {
    const msg = buildOtpMailMessage("user@example.com", "123456");
    expect(msg.to).toEqual(["user@example.com"]);
    expect(msg.message.text).toContain("123456");
    expect(msg.message.html).toContain("123456");
    expect(msg.message.subject).toBeTruthy();
  });
});

describe("requestOtpForEmail", () => {
  it("stores a hashed code with a 10 minute expiry and writes a mail document", async () => {
    await requestOtpForEmail("user@example.com");

    const otpSnap = await mockDb.collection("otpCodes").doc("user@example.com").get();
    expect(otpSnap.exists).toBe(true);
    const record = otpSnap.data();
    expect(record.attempts).toBe(0);
    expect(typeof record.hash).toBe("string");
    expect(record.expiresAt.toMillis()).toBeGreaterThan(Date.now());
    expect(record.expiresAt.toMillis() - record.createdAt.toMillis()).toBe(OTP_TTL_MS);
  });
});

describe("verifyOtpForEmail", () => {
  async function seedOtp(
    email: string,
    code: string,
    overrides: Partial<{ attempts: number; expiresAt: Timestamp }> = {},
  ) {
    await mockDb.collection("otpCodes").doc(email).set({
      hash: hashOtpCode(email, code),
      attempts: overrides.attempts ?? 0,
      expiresAt: overrides.expiresAt ?? Timestamp.fromMillis(Date.now() + OTP_TTL_MS),
      createdAt: Timestamp.now(),
    });
  }

  it("throws failed-precondition when no code was requested", async () => {
    await expect(verifyOtpForEmail("nobody@example.com", "123456", "uid-1", true)).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });

  it("throws resource-exhausted once attempts are exhausted", async () => {
    await seedOtp("user@example.com", "123456", { attempts: MAX_ATTEMPTS });
    await expect(verifyOtpForEmail("user@example.com", "123456", "uid-1", true)).rejects.toMatchObject({
      code: "resource-exhausted",
    });
  });

  it("throws deadline-exceeded when the code has expired", async () => {
    await seedOtp("user@example.com", "123456", { expiresAt: Timestamp.fromMillis(Date.now() - 1000) });
    await expect(verifyOtpForEmail("user@example.com", "123456", "uid-1", true)).rejects.toMatchObject({
      code: "deadline-exceeded",
    });
  });

  it("increments attempts and rejects on a wrong code", async () => {
    await seedOtp("user@example.com", "123456");
    await expect(verifyOtpForEmail("user@example.com", "000000", "uid-1", true)).rejects.toMatchObject({
      code: "invalid-argument",
    });
    const snap = await mockDb.collection("otpCodes").doc("user@example.com").get();
    expect(snap.data().attempts).toBe(1);
  });

  it("upgrades an anonymous caller in place when no account exists for the email", async () => {
    await seedOtp("new@example.com", "123456");
    const result = await verifyOtpForEmail("new@example.com", "123456", "anon-uid", true);
    expect(result).toEqual({ customToken: "custom-token-anon-uid" });

    const snap = await mockDb.collection("otpCodes").doc("new@example.com").get();
    expect(snap.exists).toBe(false);
  });

  it("rejects a non-anonymous caller claiming a brand-new email", async () => {
    await seedOtp("new@example.com", "123456");
    await expect(verifyOtpForEmail("new@example.com", "123456", "some-uid", false)).rejects.toMatchObject({
      code: "failed-precondition",
    });
  });

  it("returns a fresh token when the email already belongs to the caller", async () => {
    mockAuth._seedUser({ uid: "uid-1", email: "user@example.com", emailVerified: false });
    await seedOtp("user@example.com", "123456");

    const result = await verifyOtpForEmail("user@example.com", "123456", "uid-1", false);
    expect(result).toEqual({ customToken: "custom-token-uid-1" });
  });

  it("reports alreadyExists with a custom token for the existing account's uid", async () => {
    mockAuth._seedUser({ uid: "other-uid", email: "taken@example.com", emailVerified: true });
    await seedOtp("taken@example.com", "123456");

    const result = await verifyOtpForEmail("taken@example.com", "123456", "anon-uid", true);
    expect(result).toEqual({
      alreadyExists: true,
      customToken: "custom-token-other-uid",
      userId: "other-uid",
    });
  });
});
