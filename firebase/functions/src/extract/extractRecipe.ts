import { HttpsError, onCall } from "firebase-functions/v2/https";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import type { ExtractedRecipe } from "./parse";
import { fetchHtml, parseRecipeFromHtml, validateUrl } from "./parse";

const RATE_LIMIT = 30;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;

export type ExtractRecipeRequest = { url: string };

async function checkRateLimit(uid: string): Promise<void> {
  const db = getFirestore();
  const since = Timestamp.fromMillis(Date.now() - RATE_LIMIT_WINDOW_MS);
  const snapshot = await db
    .collection("users")
    .doc(uid)
    .collection("importEvents")
    .where("createdAt", ">", since)
    .count()
    .get();
  if (snapshot.data().count >= RATE_LIMIT) {
    throw new HttpsError("resource-exhausted", "Rate limit exceeded");
  }
}

async function recordImportEvent(uid: string): Promise<void> {
  const db = getFirestore();
  await db.collection("users").doc(uid).collection("importEvents").add({
    createdAt: Timestamp.now(),
  });
}

export async function runExtractRecipe(uid: string, url: string): Promise<ExtractedRecipe> {
  await checkRateLimit(uid);

  const validated = await validateUrl(url);
  const { html, finalUrl } = await fetchHtml(validated);
  const result = parseRecipeFromHtml(html, finalUrl);

  await recordImportEvent(uid);
  return result;
}

export const extractRecipe = onCall<ExtractRecipeRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }
  const { url } = request.data ?? {};
  if (!url || typeof url !== "string") {
    throw new HttpsError("invalid-argument", "A recipe url is required");
  }

  try {
    return await runExtractRecipe(request.auth.uid, url);
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    throw new HttpsError("invalid-argument", e instanceof Error ? e.message : String(e));
  }
});
