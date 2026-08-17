import { randomBytes } from "node:crypto";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

const PUBLIC_APP_ORIGIN = "https://basil.joetr.com";
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{20,80}$/;

export type CreateSharedRecipeRequest = { recipeId: string };
export type SharedRecipeRequest = { token: string };

export type SharedRecipeStep = {
  text: string;
  minutes: number | null;
};

export type SharedRecipe = {
  token: string;
  url: string;
  title: string;
  description: string | null;
  imageUrl: string | null;
  sourceUrl: string | null;
  servings: number | null;
  prepMinutes: number | null;
  cookMinutes: number | null;
  ingredients: string[];
  steps: SharedRecipeStep[];
  tags: string[];
};

function requireToken(value: unknown): string {
  if (typeof value !== "string" || !TOKEN_PATTERN.test(value)) {
    throw new HttpsError("invalid-argument", "A valid share token is required");
  }
  return value;
}

function requireRecipeId(value: unknown): string {
  if (typeof value !== "string" || value.length === 0 || value.length > 128) {
    throw new HttpsError("invalid-argument", "A valid recipe id is required");
  }
  return value;
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function optionalInteger(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) ? value : null;
}

function stringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
    .slice(0, 200);
}

function sharedSteps(value: unknown): SharedRecipeStep[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is { text?: unknown; minutes?: unknown } => typeof item === "object" && item !== null)
    .map((item) => ({
      text: typeof item.text === "string" ? item.text.trim() : "",
      minutes: optionalInteger(item.minutes),
    }))
    .filter((item) => item.text.length > 0)
    .slice(0, 200);
}

function sanitizeRecipe(data: FirebaseFirestore.DocumentData): Omit<SharedRecipe, "token" | "url"> {
  const title = optionalString(data.title) ?? "Untitled recipe";
  return {
    title: title.slice(0, 200),
    description: optionalString(data.description)?.slice(0, 4_000) ?? null,
    imageUrl: optionalString(data.imageUrl),
    sourceUrl: optionalString(data.sourceUrl),
    servings: optionalInteger(data.servings),
    prepMinutes: optionalInteger(data.prepMinutes),
    cookMinutes: optionalInteger(data.cookMinutes),
    ingredients: stringArray(data.ingredients),
    steps: sharedSteps(data.steps),
    tags: stringArray(data.tags).slice(0, 50),
  };
}

function sharedDocument(token: string) {
  return getFirestore().collection("sharedRecipes").doc(token);
}

export const createSharedRecipe = onCall<CreateSharedRecipeRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }
  if (request.auth.token.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError(
      "failed-precondition",
      "Sign in to create a share link. You can still share the recipe as text.",
    );
  }

  const recipeId = requireRecipeId(request.data?.recipeId);
  const recipeSnapshot = await getFirestore()
    .doc(`users/${request.auth.uid}/recipes/${recipeId}`)
    .get();
  if (!recipeSnapshot.exists) {
    throw new HttpsError("not-found", "Recipe not found");
  }
  const recipe = recipeSnapshot.data();
  if (!recipe || recipe.deleted === true) {
    throw new HttpsError("not-found", "Recipe not found");
  }

  const token = randomBytes(24).toString("base64url");
  const now = Timestamp.now();
  await sharedDocument(token).set({
    ownerId: request.auth.uid,
    recipeId,
    recipe: sanitizeRecipe(recipe),
    createdAt: now,
    updatedAt: now,
    revokedAt: null,
  });

  return {
    token,
    url: `${PUBLIC_APP_ORIGIN}/share/${token}`,
  } satisfies Pick<SharedRecipe, "token" | "url">;
});

export const getSharedRecipe = onCall<SharedRecipeRequest>(async (request) => {
  const token = requireToken(request.data?.token);
  const snapshot = await sharedDocument(token).get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "This share link is no longer available");
  }

  const data = snapshot.data();
  if (!data || data.revokedAt !== null) {
    throw new HttpsError("not-found", "This share link is no longer available");
  }

  const recipe = data.recipe as Omit<SharedRecipe, "token" | "url">;
  return {
    token,
    url: `${PUBLIC_APP_ORIGIN}/share/${token}`,
    ...recipe,
  } satisfies SharedRecipe;
});

export const revokeSharedRecipe = onCall<SharedRecipeRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }
  const token = requireToken(request.data?.token);
  const reference = sharedDocument(token);
  const snapshot = await reference.get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Share link not found");
  }
  if (snapshot.data()?.ownerId !== request.auth.uid) {
    throw new HttpsError("permission-denied", "You cannot revoke this share link");
  }

  await reference.update({ revokedAt: Timestamp.now() });
  return { revoked: true };
});
