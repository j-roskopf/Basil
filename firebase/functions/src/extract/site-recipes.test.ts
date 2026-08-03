import { describe, expect, it } from "vitest";
import { parseIsoDuration, parseRecipeFromHtml } from "./parse";
import { loadFixture, type FixtureName } from "./fixtures";

type SiteExpectation = {
  fixture: FixtureName;
  sourceUrl: string;
  title: string;
  minIngredients: number;
  minSteps: number;
  image?: boolean;
  prepMinutes?: number | null;
  cookMinutes?: number | null;
  servings?: number | null;
  firstIngredientIncludes?: string;
  firstStepIncludes?: string;
};

const SITES: SiteExpectation[] = [
  {
    fixture: "site-allrecipes",
    sourceUrl: "https://www.allrecipes.com/recipe/21014/good-old-fashioned-pancakes/",
    title: "Good Old-Fashioned Pancakes",
    minIngredients: 7,
    minSteps: 5,
    image: true,
    prepMinutes: 5,
    cookMinutes: 15,
    servings: 8,
    firstIngredientIncludes: "flour",
    firstStepIncludes: "Gather",
  },
  {
    fixture: "site-food-network",
    sourceUrl: "https://www.foodnetwork.com/recipes/alton-brown/baked-macaroni-and-cheese-recipe-1939524",
    title: "Baked Macaroni and Cheese",
    minIngredients: 10,
    minSteps: 4,
    image: true,
    prepMinutes: 20,
    cookMinutes: 45,
    servings: 6,
    firstIngredientIncludes: "macaroni",
    firstStepIncludes: "Preheat",
  },
  {
    fixture: "site-food-com",
    sourceUrl: "https://www.food.com/recipe/best-ever-banana-bread-2886",
    title: "Best Banana Bread",
    minIngredients: 6,
    minSteps: 5,
    image: true,
    prepMinutes: 10,
    cookMinutes: 60,
    firstIngredientIncludes: "butter",
  },
  {
    fixture: "site-serious-eats",
    sourceUrl: "https://www.seriouseats.com/the-food-lab-best-chocolate-chip-cookie-recipe",
    title: "The Food Lab's Chocolate Chip Cookies",
    minIngredients: 8,
    minSteps: 4,
    image: true,
    prepMinutes: 20,
    cookMinutes: 30,
    firstIngredientIncludes: "butter",
    firstStepIncludes: "Melt butter",
  },
  {
    fixture: "site-simply-recipes",
    sourceUrl: "https://www.simplyrecipes.com/recipes/perfect_guacamole/",
    title: "How to Make the Best Guacamole",
    minIngredients: 6,
    minSteps: 3,
    image: true,
    prepMinutes: 10,
    servings: 4,
    firstIngredientIncludes: "avocado",
    firstStepIncludes: "avocado",
  },
  {
    fixture: "site-epicurious",
    sourceUrl: "https://www.epicurious.com/recipes/food/views/easy-banana-bread-recipe",
    title: "Easy Classic Banana Bread",
    minIngredients: 8,
    minSteps: 3,
    image: true,
    servings: 9,
    firstIngredientIncludes: "nonstick",
  },
  {
    fixture: "site-sallys-baking",
    sourceUrl: "https://sallysbakingaddiction.com/chewy-chocolate-chip-cookies/",
    title: "Chewy Chocolate Chip Cookies",
    minIngredients: 8,
    minSteps: 5,
    image: true,
    prepMinutes: 15,
    cookMinutes: 13,
    servings: 16,
    firstIngredientIncludes: "flour",
  },
  {
    fixture: "site-smitten-kitchen",
    sourceUrl: "https://smittenkitchen.com/2010/08/everyday-chocolate-cake/",
    title: "Everyday Chocolate Cake",
    minIngredients: 10,
    minSteps: 3,
    image: true,
    firstIngredientIncludes: "butter",
    firstStepIncludes: "oven",
  },
  {
    fixture: "site-woks-of-life",
    sourceUrl: "https://thewoksoflife.com/ma-po-tofu-real-deal/",
    title: "Mapo Tofu",
    minIngredients: 10,
    minSteps: 4,
    image: true,
    prepMinutes: 10,
    cookMinutes: 25,
    servings: 6,
    firstIngredientIncludes: "oil",
  },
  {
    fixture: "site-nyt-cooking",
    sourceUrl: "https://cooking.nytimes.com/recipes/1015819-chocolate-chip-cookies",
    title: "Best Chocolate Chip Cookies",
    minIngredients: 8,
    minSteps: 3,
    image: true,
    servings: 18,
    firstIngredientIncludes: "flour",
    firstStepIncludes: "Sift",
  },
];

describe("real recipe sites", () => {
  for (const site of SITES) {
    it(`extracts ${site.fixture.replace("site-", "")}`, () => {
      const html = loadFixture(site.fixture);
      const recipe = parseRecipeFromHtml(html, site.sourceUrl);

      expect(recipe.confidence).toBe("FULL");
      expect(recipe.title).toBe(site.title);
      expect(recipe.sourceUrl).toBe(site.sourceUrl);
      expect(recipe.ingredients.length).toBeGreaterThanOrEqual(site.minIngredients);
      expect(recipe.steps.length).toBeGreaterThanOrEqual(site.minSteps);
      expect(recipe.ingredients.every((i) => i.trim().length > 0)).toBe(true);
      expect(recipe.steps.every((s) => s.text.trim().length > 0)).toBe(true);

      if (site.image !== false) {
        expect(recipe.imageUrl).toMatch(/^https?:\/\//);
        expect(recipe.imageUrl).not.toContain("[object");
      }
      if (site.prepMinutes !== undefined) {
        expect(recipe.prepMinutes).toBe(site.prepMinutes);
      }
      if (site.cookMinutes !== undefined) {
        expect(recipe.cookMinutes).toBe(site.cookMinutes);
      }
      if (site.servings !== undefined) {
        expect(recipe.servings).toBe(site.servings);
      }
      if (site.firstIngredientIncludes) {
        expect(recipe.ingredients[0].toLowerCase()).toContain(site.firstIngredientIncludes.toLowerCase());
      }
      if (site.firstStepIncludes) {
        expect(recipe.steps[0].text.toLowerCase()).toContain(site.firstStepIncludes.toLowerCase());
      }
    });
  }
});

describe("parseIsoDuration", () => {
  it("parses common and Food Network-style durations", () => {
    expect(parseIsoDuration("PT15M")).toBe(15);
    expect(parseIsoDuration("PT1H30M")).toBe(90);
    expect(parseIsoDuration("P0Y0M0DT0H20M0.000S")).toBe(20);
    expect(parseIsoDuration("P0Y0M0DT0H45M0.000S")).toBe(45);
    expect(parseIsoDuration("P0DT1H5M")).toBe(65);
    expect(parseIsoDuration(null)).toBeUndefined();
  });
});
