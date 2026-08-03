import { onRequest } from "firebase-functions/v2/https";
import { validateUrl } from "./extract/parse";

const MAX_REDIRECTS = 3;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const IMAGE_USER_AGENT =
  "Mozilla/5.0 (compatible; Basil image proxy; +https://basil.joetr.com)";

/**
 * Serves recipe images through the app's origin. Browser WASM image decoding
 * fetches bytes through Ktor, so many otherwise embeddable third-party images
 * fail when their source host does not opt into CORS.
 */
export const proxyImage = onRequest({ cors: true }, async (request, response) => {
  if (request.method !== "GET") {
    response.status(405).send("Method not allowed");
    return;
  }

  const rawUrl = request.query.url;
  if (typeof rawUrl !== "string" || rawUrl.length === 0 || rawUrl.length > 2_048) {
    response.status(400).send("A valid image url is required");
    return;
  }

  try {
    const upstream = await fetchImage(new URL(rawUrl));
    if (!upstream.ok) {
      response.status(502).send(`Image fetch failed with HTTP ${upstream.status}`);
      return;
    }

    const contentType = (upstream.headers.get("content-type") ?? "")
      .split(";", 1)[0]
      .trim()
      .toLowerCase();
    if (!contentType.startsWith("image/")) {
      response.status(415).send("Upstream response is not an image");
      return;
    }

    const contentLength = Number(upstream.headers.get("content-length") ?? 0);
    if (contentLength > MAX_IMAGE_BYTES) {
      response.status(413).send("Image is too large");
      return;
    }

    const bytes = Buffer.from(await upstream.arrayBuffer());
    if (bytes.byteLength > MAX_IMAGE_BYTES) {
      response.status(413).send("Image is too large");
      return;
    }

    response.set("Content-Type", contentType);
    response.set("Cache-Control", "public, max-age=3600, s-maxage=86400");
    response.set("X-Content-Type-Options", "nosniff");
    response.status(200).send(bytes);
  } catch (error) {
    response.status(400).send(error instanceof Error ? error.message : "Image fetch failed");
  }
});

async function fetchImage(start: URL): Promise<Response> {
  let current = start;
  for (let redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
    await validateUrl(current.toString());
    const upstream = await fetch(current, {
      redirect: "manual",
      headers: {
        Accept: "image/webp,image/png,image/jpeg,image/gif,*/*;q=0.1",
        "User-Agent": IMAGE_USER_AGENT,
      },
    });

    if (upstream.status < 300 || upstream.status >= 400) return upstream;
    const location = upstream.headers.get("location");
    if (!location) return upstream;
    current = new URL(location, current);
  }
  throw new Error("Too many redirects");
}
