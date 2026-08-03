import { describe, expect, it } from "vitest";
import { extractJsonLd, extractMicrodata, heuristicExtract, parseRecipeFromHtml, parseStepMinutes } from "./parse";
import { loadFixture } from "./fixtures";

describe("parseStepMinutes", () => {
  it("parses common minute/hour phrasings", () => {
    expect(parseStepMinutes("Bake 15 minutes")).toBe(15);
    expect(parseStepMinutes("Cook 20-25 minutes")).toBe(25);
    expect(parseStepMinutes("Simmer 1 hour")).toBe(60);
    expect(parseStepMinutes("Rest half an hour")).toBe(30);
    expect(parseStepMinutes("overnight")).toBeNull();
  });
});

describe("fixture: json-ld-basic — standard Recipe schema", () => {
  it("extracts full recipe details", () => {
    const html = loadFixture("json-ld-basic");
    const recipe = parseRecipeFromHtml(html, "https://example.com/soup");

    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Classic Tomato Soup");
    expect(recipe.description).toBe("A simple weeknight tomato soup.");
    expect(recipe.imageUrl).toBe("https://example.com/soup.jpg");
    expect(recipe.ingredients.length).toBe(3);
    expect(recipe.steps.length).toBe(3);
    expect(recipe.steps[0].minutes).toBe(5);
    expect(recipe.steps[2].minutes).toBe(20);
  });
});

describe("fixture: json-ld-graph — Recipe inside @graph", () => {
  it("finds the recipe node within @graph", () => {
    const html = loadFixture("json-ld-graph");
    const recipe = parseRecipeFromHtml(html);

    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Lemon Drizzle Cake");
    expect(recipe.ingredients.length).toBe(4);
    expect(recipe.steps.length).toBe(4);
    expect(recipe.imageUrl).toBe("https://example.com/cake-1.jpg");
  });
});

describe("fixture: json-ld-howto-section — HowToSection instructions", () => {
  it("flattens HowToSection itemListElement steps", () => {
    const html = loadFixture("json-ld-howto-section");
    const recipe = parseRecipeFromHtml(html);

    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Beef Bourguignon");
    expect(recipe.ingredients.length).toBe(3);
    expect(recipe.steps.length).toBe(4);
    expect(recipe.steps[0].text).toBe("Cut beef into cubes and pat dry.");
    expect(recipe.steps[3].minutes).toBe(120);
  });
});

describe("fixture: json-ld-array — top-level JSON-LD array", () => {
  it("finds Recipe within a top-level array", () => {
    const html = loadFixture("json-ld-array");
    const recipe = parseRecipeFromHtml(html);

    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Shakshuka");
    expect(recipe.ingredients.length).toBe(3);
    expect(recipe.steps.length).toBe(3);
  });
});

describe("fixture: microdata — itemprop recipe fields", () => {
  it("extracts microdata fields", () => {
    const html = loadFixture("microdata");
    const micro = extractMicrodata(html)!;
    const recipe = parseRecipeFromHtml(html);

    expect(micro.confidence).toBe("FULL");
    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Cacio e Pepe");
    expect(recipe.description).toBe("Roman pasta with pecorino and black pepper.");
    expect(recipe.ingredients.length).toBe(3);
    expect(recipe.steps.length).toBe(3);
    expect(recipe.steps[0].minutes).toBe(8);
    expect(recipe.imageUrl).toBe("https://example.com/pasta.jpg");
  });
});

describe("fixture: heuristic-blog — no structured markup", () => {
  it("falls back to heuristic list extraction", () => {
    const html = loadFixture("heuristic-blog");
    const recipe = parseRecipeFromHtml(html);

    expect(extractJsonLd(html)).toBeNull();
    expect(extractMicrodata(html)).toBeNull();
    // Heuristic that recovers both ingredients and steps is treated as FULL.
    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Grandma's Banana Bread");
    expect(recipe.ingredients.length).toBe(5);
    expect(recipe.steps.length).toBe(4);
    expect(recipe.steps[3].minutes).toBe(60);
  });
});

describe("fixture: minimal-metadata — og tags only, no recipe body", () => {
  it("reports NONE confidence but still fills metadata", () => {
    const html = loadFixture("minimal-metadata");
    const recipe = parseRecipeFromHtml(html);

    expect(recipe.confidence).toBe("NONE");
    expect(recipe.title).toBe("Mystery Dish");
    expect(recipe.imageUrl).toBe("https://example.com/mystery.jpg");
    expect(recipe.ingredients.length).toBe(0);
    expect(recipe.steps.length).toBe(0);
    expect(recipe.rawText?.includes("Coming Soon")).toBe(true);
  });
});

describe("heuristicExtract", () => {
  it("returns NONE when lists are missing", () => {
    const html = "<html><body><p>Just text</p></body></html>";
    const result = heuristicExtract(html);
    expect(result.confidence).toBe("NONE");
    expect(result.ingredients?.length ?? 0).toBe(0);
    expect(result.steps?.length ?? 0).toBe(0);
  });
});

describe("fixture: open-graph — metadata only", () => {
  it("extracts title and image from og tags", () => {
    const html = loadFixture("open-graph");
    const recipe = parseRecipeFromHtml(html, "https://example.com/pasta");
    expect(recipe.title).toBe("OG Pasta");
    expect(recipe.imageUrl).toBe("https://example.com/pasta.jpg");
  });
});

describe("fixture: schema-itemscope — microdata yield and times", () => {
  it("extracts ingredients from itemscope markup", () => {
    const html = loadFixture("schema-itemscope");
    const recipe = parseRecipeFromHtml(html, "https://example.com/bread");
    expect(recipe.title).toBe("Banana Bread");
    expect(recipe.ingredients.length).toBe(2);
  });
});

describe("fixture: json-ld-nested — loads nested markup", () => {
  it("captures readable text content", () => {
    const html = loadFixture("json-ld-nested");
    const recipe = parseRecipeFromHtml(html, "https://example.com/curry");
    expect(recipe.rawText?.includes("Nested Curry")).toBe(true);
  });
});

describe("fixture: wordpress-wprm — loads wprm markup", () => {
  it("preserves the source URL", () => {
    const html = loadFixture("wordpress-wprm");
    const recipe = parseRecipeFromHtml(html, "https://example.com/chicken");
    expect(html.includes("wprm-recipe-ingredients")).toBe(true);
    expect(recipe.sourceUrl).toBe("https://example.com/chicken");
  });
});

describe("fixture: allrecipes-pancakes — ImageObject + dual @type", () => {
  it("extracts ingredients, steps, image url, and times", () => {
    const html = loadFixture("allrecipes-pancakes");
    const recipe = parseRecipeFromHtml(html, "https://www.allrecipes.com/recipe/21014/");

    expect(recipe.confidence).toBe("FULL");
    expect(recipe.title).toBe("Good Old-Fashioned Pancakes");
    expect(recipe.ingredients.length).toBe(7);
    expect(recipe.steps.length).toBe(5);
    expect(recipe.imageUrl).toMatch(/^https?:\/\//);
    expect(recipe.imageUrl).not.toBe("[object Object]");
    expect(recipe.prepMinutes).toBe(5);
    expect(recipe.cookMinutes).toBe(15);
    expect(recipe.servings).toBe(8);
  });
});
