import { readFileSync } from "node:fs";
import { join } from "node:path";

const FIXTURE_NAMES = [
  "json-ld-basic",
  "json-ld-graph",
  "json-ld-howto-section",
  "json-ld-array",
  "json-ld-nested",
  "microdata",
  "schema-itemscope",
  "heuristic-blog",
  "minimal-metadata",
  "open-graph",
  "wordpress-wprm",
  "allrecipes-pancakes",
  // Real-site fixtures (captured HTML minimized to JSON-LD / recipe cards).
  "site-allrecipes",
  "site-food-network",
  "site-food-com",
  "site-serious-eats",
  "site-simply-recipes",
  "site-epicurious",
  "site-sallys-baking",
  "site-smitten-kitchen",
  "site-woks-of-life",
  "site-nyt-cooking",
] as const;

export type FixtureName = (typeof FIXTURE_NAMES)[number];

const cache = new Map<FixtureName, string>();

export function loadFixture(name: FixtureName): string {
  const cached = cache.get(name);
  if (cached !== undefined) return cached;
  const html = readFileSync(join(__dirname, "fixtures", `${name}.html`), "utf8");
  cache.set(name, html);
  return html;
}
