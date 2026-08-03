import { resolve4 } from "node:dns/promises";

export type RecipeStep = { text: string; minutes?: number | null };
export type ExtractionConfidence = "FULL" | "PARTIAL" | "NONE";
export type ExtractedRecipe = {
  confidence: ExtractionConfidence;
  title?: string | null;
  description?: string | null;
  imageUrl?: string | null;
  sourceUrl?: string | null;
  servings?: number | null;
  prepMinutes?: number | null;
  cookMinutes?: number | null;
  ingredients: string[];
  steps: RecipeStep[];
  tags: string[];
  rawText?: string | null;
};

const MAX_REDIRECTS = 3;
const MAX_BYTES = 2 * 1024 * 1024;

export function parseStepMinutes(text: string): number | null {
  if (/overnight/i.test(text)) return null;
  const range = text.match(/(\d+)\s*(?:-|–|to)\s*(\d+)\s*(?:min(?:ute)?s?|m)\b/i);
  if (range) return Number(range[2]);
  const hours = text.match(/(\d+)\s*(?:hr|hour|hours|h)\b/i);
  if (hours) return Number(hours[1]) * 60;
  if (/half\s+an?\s+hour/i.test(text)) return 30;
  if (/an?\s+hour\b/i.test(text)) return 60;
  const mins = text.match(/(\d+)\s*(?:min(?:ute)?s?|m)\b/i);
  return mins ? Number(mins[1]) : null;
}

export function isPrivateIp(host: string): boolean {
  if (host === "localhost" || host.endsWith(".localhost")) return true;
  if (host === "169.254.169.254") return true;
  const m = host.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);
  if (!m) return false;
  const [a, b] = [Number(m[1]), Number(m[2])];
  if (a === 10) return true;
  if (a === 127) return true;
  if (a === 0) return true;
  if (a === 169 && b === 254) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  if (a === 100 && b >= 64 && b <= 127) return true;
  return false;
}

export async function validateUrl(raw: string): Promise<URL> {
  const url = new URL(raw);
  if (url.protocol !== "http:" && url.protocol !== "https:") throw new Error("Invalid scheme");
  if (isPrivateIp(url.hostname)) throw new Error("SSRF blocked");
  const address = await resolve4(url.hostname).then(
    (addrs) => addrs[0],
    () => url.hostname,
  );
  if (isPrivateIp(address)) throw new Error("SSRF blocked");
  return url;
}

const BROWSER_UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

export function looksLikeBotChallenge(html: string): boolean {
  const sample = html.slice(0, 4_000);
  return (
    /Enable JavaScript and cookies to continue/i.test(sample) ||
    /cf-browser-verification/i.test(sample) ||
    /Attention Required! \| Cloudflare/i.test(sample) ||
    (/captcha/i.test(sample) && sample.length < 8_000)
  );
}

export async function fetchHtml(url: URL): Promise<{ html: string; finalUrl: string }> {
  let current = url;
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);
    let res: Response;
    try {
      res = await fetch(current.toString(), {
        redirect: "manual",
        signal: controller.signal,
        headers: {
          "User-Agent": BROWSER_UA,
          Accept: "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
          "Accept-Language": "en-US,en;q=0.9",
        },
      });
    } finally {
      clearTimeout(timeout);
    }
    if (res.status >= 300 && res.status < 400) {
      const loc = res.headers.get("location");
      if (!loc) break;
      current = new URL(loc, current);
      await validateUrl(current.toString());
      continue;
    }
    if (res.status >= 400) {
      throw new Error(`Fetch failed with HTTP ${res.status}`);
    }
    const type = res.headers.get("content-type") ?? "";
    if (!type.includes("text/html")) throw new Error("Not HTML");
    const buf = new Uint8Array(await res.arrayBuffer());
    if (buf.byteLength > MAX_BYTES) throw new Error("Too large");
    const html = new TextDecoder().decode(buf);
    if (looksLikeBotChallenge(html)) {
      throw new Error("This site blocked automatic import.");
    }
    return { html, finalUrl: current.toString() };
  }
  throw new Error("Too many redirects");
}

function decodeHtmlEntities(text: string): string {
  return text
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCharCode(parseInt(h, 16)))
    .replace(/&nbsp;/g, " ");
}

function stripTags(html: string): string {
  return decodeHtmlEntities(html.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim());
}

function resolveImageUrl(image: unknown): string | undefined {
  if (!image) return undefined;
  if (typeof image === "string") return image;
  if (Array.isArray(image)) {
    for (const item of image) {
      const resolved = resolveImageUrl(item);
      if (resolved) return resolved;
    }
    return undefined;
  }
  if (typeof image === "object") {
    const obj = image as Record<string, unknown>;
    if (typeof obj.url === "string") return obj.url;
    if (typeof obj.contentUrl === "string") return obj.contentUrl;
  }
  return undefined;
}

/** Supports PT15M, P0DT20M, and Food Network-style P0Y0M0DT0H20M0.000S. */
export function parseIsoDuration(value: unknown): number | undefined {
  if (typeof value !== "string") return undefined;
  const raw = value.trim();
  const match = raw.match(
    /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/i,
  );
  if (!match) return undefined;
  const years = Number(match[1] ?? 0);
  const months = Number(match[2] ?? 0);
  const days = Number(match[3] ?? 0);
  const hours = Number(match[4] ?? 0);
  const minutes = Number(match[5] ?? 0);
  const seconds = Number(match[6] ?? 0);
  // Approximate months/years only if somehow present; recipes use H/M/S.
  const total =
    years * 365 * 24 * 60 +
    months * 30 * 24 * 60 +
    days * 24 * 60 +
    hours * 60 +
    minutes +
    (seconds >= 30 ? 1 : 0);
  return total > 0 ? total : undefined;
}

function parseYield(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return Math.round(value);
  if (typeof value === "string") {
    const m = value.match(/(\d+)/);
    return m ? Number(m[1]) : undefined;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const parsed = parseYield(item);
      if (parsed) return parsed;
    }
  }
  return undefined;
}

export function extractJsonLd(html: string): Partial<ExtractedRecipe> | null {
  const scripts = [...html.matchAll(/<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi)];
  for (const [, body] of scripts) {
    try {
      const data = JSON.parse(body);
      const nodes = Array.isArray(data) ? data : data["@graph"] ?? [data];
      const recipe = nodes.find((n: Record<string, unknown>) =>
        n["@type"] === "Recipe" || (Array.isArray(n["@type"]) && n["@type"].includes("Recipe"))
      );
      if (!recipe) continue;
      const ingredients = (recipe.recipeIngredient ?? []).map(String);
      const instructions = recipe.recipeInstructions ?? [];
      const steps: RecipeStep[] = [];
      for (const item of instructions) {
        if (typeof item === "string") steps.push({ text: item, minutes: parseStepMinutes(item) });
        else if (item?.text) steps.push({ text: String(item.text), minutes: parseStepMinutes(String(item.text)) });
        else if (item?.itemListElement) {
          for (const sub of item.itemListElement) {
            const text = String(sub.text ?? sub.name ?? "");
            if (text) steps.push({ text, minutes: parseStepMinutes(text) });
          }
        }
      }
      return {
        confidence: "FULL",
        title: recipe.name
          ? decodeHtmlEntities(String(recipe.name))
          : recipe.headline
            ? decodeHtmlEntities(String(recipe.headline))
            : undefined,
        description: recipe.description ? decodeHtmlEntities(String(recipe.description)) : undefined,
        imageUrl: resolveImageUrl(recipe.image),
        servings: parseYield(recipe.recipeYield) ?? null,
        prepMinutes: parseIsoDuration(recipe.prepTime) ?? null,
        cookMinutes: parseIsoDuration(recipe.cookTime) ?? null,
        ingredients: ingredients.map(decodeHtmlEntities),
        steps: steps.map((s) => ({ ...s, text: decodeHtmlEntities(s.text) })),
      };
    } catch {
      /* continue */
    }
  }
  return null;
}

function stepsFromProseHtml(blockHtml: string): RecipeStep[] {
  // Jetpack sometimes omits the opening <p> on the first paragraph ("text...</p><p>...").
  let normalized = blockHtml.trim();
  if (normalized && !/^<p[\s>]/i.test(normalized) && /<\/p>/i.test(normalized)) {
    normalized = `<p>${normalized}`;
  }
  const paragraphs = [...normalized.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)]
    .map((m) => stripTags(m[1]))
    .filter((t) => t.length > 0);
  if (paragraphs.length >= 1) {
    return paragraphs.map((text) => ({ text, minutes: parseStepMinutes(text) }));
  }
  const text = stripTags(blockHtml);
  if (!text) return [];
  return [{ text, minutes: parseStepMinutes(text) }];
}

/** Jetpack / h-recipe style cards (Smitten Kitchen, etc.). */
export function extractRecipeCard(html: string): Partial<ExtractedRecipe> | null {
  const ingredients = [
    ...html.matchAll(
      /<(?:li|span|div)[^>]+(?:itemprop=["']recipeIngredient["']|jetpack-recipe-ingredient|p-ingredient)[^>]*>([\s\S]*?)<\/(?:li|span|div)>/gi,
    ),
  ]
    .map((m) => stripTags(m[1]))
    .filter(Boolean);

  const directionBlocks = [
    ...html.matchAll(
      /<(?:div|section)[^>]+(?:jetpack-recipe-directions|e-instructions|recipe-directions|recipe__instructions)[^>]*>([\s\S]*?)<\/(?:div|section)>/gi,
    ),
  ];
  const steps = directionBlocks.flatMap((m) => stepsFromProseHtml(m[1]));

  if (!ingredients.length && !steps.length) return null;

  const titleMatch =
    html.match(/<(?:h1|h2|h3)[^>]+(?:jetpack-recipe-title|p-name)[^>]*>([\s\S]*?)<\/(?:h1|h2|h3)>/i) ||
    html.match(/<[^>]+itemprop=["']name["'][^>]*>([\s\S]*?)<\/[^>]+>/i);

  return {
    confidence: ingredients.length && steps.length ? "FULL" : "PARTIAL",
    title: titleMatch ? stripTags(titleMatch[1]) : undefined,
    ingredients,
    steps,
  };
}

export function extractMicrodata(html: string): Partial<ExtractedRecipe> | null {
  const ingredients = [...html.matchAll(/<[^>]+itemprop=["']recipeIngredient["'][^>]*>([\s\S]*?)<\/[^>]+>/gi)]
    .map((m) => stripTags(m[1]))
    .filter(Boolean);
  let steps = [...html.matchAll(/<[^>]+itemprop=["']recipeInstructions["'][^>]*>([\s\S]*?)<\/[^>]+>/gi)]
    .map((m) => {
      const text = stripTags(m[1]);
      return { text, minutes: parseStepMinutes(text) };
    })
    .filter((s) => s.text);

  // Microdata often marks ingredients but leaves directions in a prose card (Jetpack).
  if (!steps.length) {
    const card = extractRecipeCard(html);
    if (card?.steps?.length) {
      steps = card.steps.map((s) => ({ text: s.text, minutes: s.minutes ?? null }));
    }
  }

  if (!ingredients.length && !steps.length) return null;

  const titleMatch = html.match(/<[^>]+itemprop=["']name["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
  const descMatch = html.match(/<[^>]+itemprop=["']description["'][^>]*>([\s\S]*?)<\/[^>]+>/i);

  return {
    confidence: ingredients.length && steps.length ? "FULL" : "PARTIAL",
    title: titleMatch ? stripTags(titleMatch[1]) : undefined,
    description: descMatch ? stripTags(descMatch[1]) : undefined,
    ingredients,
    steps,
  };
}

export function heuristicExtract(html: string): Partial<ExtractedRecipe> {
  const ingredientBlocks = [...html.matchAll(/<(?:ul|ol)[^>]*>([\s\S]*?)<\/(?:ul|ol)>/gi)]
    .filter((m) => /ingredient/i.test(m[0]));
  const stepBlocks = [...html.matchAll(/<(?:ul|ol)[^>]*>([\s\S]*?)<\/(?:ul|ol)>/gi)]
    .filter((m) => /instruction|method|direction|step/i.test(m[0]));
  const li = (block: string) => [...block.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)]
    .map((m) => stripTags(m[1]))
    .filter(Boolean);
  const ingredients = ingredientBlocks.flatMap((m) => li(m[1]));
  const steps = stepBlocks.flatMap((m) => li(m[1])).map((text) => ({ text, minutes: parseStepMinutes(text) }));
  const confidence: ExtractionConfidence = ingredients.length && steps.length ? "PARTIAL" : "NONE";
  return { confidence, ingredients, steps };
}

function meta(html: string, prop: string): string | undefined {
  const og = html.match(new RegExp(`<meta[^>]+property=["']${prop}["'][^>]+content=(["'])(.*?)\\1`, "is"));
  if (og) return og[2];
  const name = html.match(new RegExp(`<meta[^>]+name=["']${prop}["'][^>]+content=(["'])(.*?)\\1`, "is"));
  return name?.[2];
}

function bestPartial(
  ...candidates: Array<Partial<ExtractedRecipe> | null | undefined>
): Partial<ExtractedRecipe> | null {
  let best: Partial<ExtractedRecipe> | null = null;
  let bestScore = -1;
  for (const candidate of candidates) {
    if (!candidate) continue;
    const score =
      (candidate.ingredients?.length ?? 0) * 2 +
      (candidate.steps?.length ?? 0) * 2 +
      (candidate.title ? 1 : 0) +
      (candidate.imageUrl ? 1 : 0);
    if (score > bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  return best;
}

export function parseRecipeFromHtml(html: string, sourceUrl = ""): ExtractedRecipe {
  const jsonLd = extractJsonLd(html);
  const microdata = extractMicrodata(html);
  const card = extractRecipeCard(html);
  const heuristic = heuristicExtract(html);
  const structured = bestPartial(jsonLd, microdata, card);

  const ingredients = structured?.ingredients?.length
    ? structured.ingredients
    : heuristic.ingredients ?? [];
  const steps = structured?.steps?.length ? structured.steps : heuristic.steps ?? [];
  // Fill missing halves across extractors (e.g. microdata ingredients + jetpack steps).
  const mergedIngredients =
    ingredients.length > 0
      ? ingredients
      : microdata?.ingredients?.length
        ? microdata.ingredients
        : card?.ingredients?.length
          ? card.ingredients
          : heuristic.ingredients ?? [];
  const mergedSteps =
    steps.length > 0
      ? steps
      : microdata?.steps?.length
        ? microdata.steps
        : card?.steps?.length
          ? card.steps
          : heuristic.steps ?? [];

  const confidence: ExtractionConfidence =
    mergedIngredients.length && mergedSteps.length
      ? "FULL"
      : mergedIngredients.length || mergedSteps.length
        ? "PARTIAL"
        : "NONE";

  const titleRaw =
    structured?.title ??
    card?.title ??
    meta(html, "og:title") ??
    html.match(/<title>([^<]+)<\/title>/i)?.[1];
  const title = titleRaw ? decodeHtmlEntities(titleRaw) : null;
  const imageUrl = structured?.imageUrl ?? meta(html, "og:image");
  const rawText = html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 20_000);

  return {
    confidence,
    title,
    description: structured?.description ? decodeHtmlEntities(structured.description) : null,
    imageUrl: imageUrl ?? null,
    sourceUrl: sourceUrl || null,
    servings: structured?.servings ?? null,
    prepMinutes: structured?.prepMinutes ?? null,
    cookMinutes: structured?.cookMinutes ?? null,
    ingredients: mergedIngredients.map(decodeHtmlEntities),
    steps: mergedSteps.map((s) => ({ ...s, text: decodeHtmlEntities(s.text) })),
    tags: [],
    rawText,
  };
}
