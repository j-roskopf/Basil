# Basil Design DNA

Extracted from the five reference App Store screenshots. This file is the visual source of truth for UI work. Where it conflicts with placeholder implementations, follow this DNA. Groceries and Calendar **features** stay out of v1 scope; their chrome (checkbox, quantity typography, tab bar, list rhythm) is in scope.

## Brand world

Warm coral/terracotta on pure white. Editorial serif display + clean grotesque UI. Thin-line icons. Airy whitespace. Flat surfaces — almost no Material elevation. Hairline dividers, not cards-in-cards.

Marketing chrome (coral board behind phone mockups, “YOUR RECIPES” captions) is **not** part of the in-app chrome, except cook mode which truly is full-bleed coral.

---

## Color tokens

| Token | Light | Dark | Role |
|---|---|---|---|
| `primary` | `#E8735F` | `#F0876F` | Accent: active tab, quantities, checks, action icons, today date |
| `onPrimary` | `#FFFFFF` | `#241512` | Text/icons on filled coral |
| `ink` | `#2B2B2E` | `#EDEEF0` | Titles, unchecked list text, active cook step |
| `inkMuted` | `#8C8C90` | `#A7A7AE` | Meta, captions, inactive steps (~faded further) |
| `inkFaint` | `#C7C7CC` | `#5A5A62` | Inactive tab icons/labels, empty checkbox ring |
| `canvas` | `#FFFFFF` | `#17181C` | App background |
| `surface` | `#FFFFFF` | `#201F24` | Sheets, cook-mode card |
| `surfaceTint` | `#FBEFEC` | `#2A2226` | Soft coral wash (chips, placeholders) |
| `outline` | `#EDEAE7` | `#302E33` | Hairline dividers |
| `accentWarm` | `#F2A33C` | `#F2A33C` | Timer accents only |

No second accent. No purple. No pure black body text.

---

## Typography

Bundled: **Fraunces** (display) + **Inter** (UI).

| Style | Family | Size / LH | Weight | Use |
|---|---|---|---|---|
| `displayLarge` | Fraunces | 34 / 40 | SemiBold | Screen titles: “All”, “Groceries”, “Calendar”, “Recipes” |
| `displayMedium` | Fraunces | 28 / 34 | SemiBold | Recipe detail title (“Torta di mele”) |
| `titleLarge` | Fraunces | 18 / 24 | SemiBold | Recipe list row titles |
| `titleMedium` | Inter | 16 / 22 | SemiBold | Ingredient group headers (“Pastry dough”) |
| `bodyLarge` | Inter | 17 / 26 | Regular | Cook-mode step body |
| `bodyMedium` | Inter | 14 / 20 | Regular | Descriptions (2-line clamp) |
| `labelMedium` | Inter | 12 / 16 | ExtraBold, +0.6 tracking, UPPER | Section labels, PREP / TOTAL |
| `labelSmall` | Inter | 11 / 14 | Regular | Meta row (time · tag · source) |
| `quantity` | Inter | 13 / 18 | Medium | Coral quantity under grocery/ingredient rows |

---

## Shape & spacing

| Token | Value |
|---|---|
| `radius.image` | 10dp (list thumbs), 16dp (hero soft) |
| `radius.card` | 20dp |
| `radius.sheet` | 36dp (cook-mode white card) |
| `radius.chip` / pill | 999dp |
| `radius.iconButton` | 999dp (circular back / more) |
| Spacing scale | 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 |
| Phone side gutter | 20dp |
| List row gap | ~20–24dp between recipe rows |
| Divider inset | Starts after leading checkbox/thumb; does not run under leading chrome |

Shadows: soft, low, ink-tinted (~6% opacity, 8–16dp blur). Prefer none on list surfaces.

---

## Iconography

Thin stroke (~1.5–2pt), rounded caps. Coral when active/action; `inkFaint` when inactive; `inkMuted` for meta.

Required glyphs:

- Recipe box (Recipes tab)
- Compass / browse (Import tab)
- Person (Account tab; also servings meta)
- Clock / stopwatch (time meta, cook timer)
- Tag (category)
- Globe (source domain)
- Shopping bag (groceries action chrome)
- Play-in-circle (Cook)
- Scale / sliders (Adjust / Edit)
- Plus, ellipsis, chevron-back, close (X)
- Sun / moon (meal slots — calendar chrome only)
- Circular checkbox (empty ring / filled coral + white check)

---

## Screen structures

### 1. Recipes — “All” list (phone)

```
[ status ]
[ … ]                          ← coral ellipsis, top-trailing
All                            ← Fraunces displayLarge, leading

┌────┐  Brownies with…         ← Fraunces titleLarge
│img │  ⏱ 40min  🏷 Sweets  🌐 vero.cooking
└────┘  Description two lines…

← 20dp gutter; 88×88 rounded-square thumb; no bordered card
← hairline optional between rows OR generous whitespace only
[ Recipes | Import | Account ] ← bottom tab bar
```

Desktop (≥840dp): keep sidebar + multi-column grid; same tokens, denser collection layout.

### 2. Recipe detail — “Torta di mele”

```
[ ◀ ]                    [ … ]   ← circular ghost buttons
        [ hero photo ]           ← large centered food image
Torta di mele                    ← Fraunces displayMedium
👤 12   |   ⏱ 45min PREP   |   115min TOTAL
▶ Cook     🛍 Groceries*     ⚖ Adjust/Edit
Description…
────────────────
Pastry dough                     ← Inter titleMedium
**500 g** light spelt flour      ← quantity bold (or coral line under name)
**40 g** sugar
```

\* Groceries action may be omitted in v1; keep Cook + Edit/Adjust.

### 3. Cook mode

```
████ coral canvas ████
   COOKING MODE
Focus on the essentials

┌──────── white sheet r=36 ────────┐
│ ✕                          ⏱     │
│                                  │
│ 1 previous step…        (faded)  │
│ 2 current step text…    (ink)    │
│ 3 next step…            (faded)  │
│                                  │
│        ( Recipe title  + )       │  ← light pill
└──────────────────────────────────┘
```

- Active step ~100% ink; neighbors ~25–35% opacity.
- Tap / swipe advances step; timer opens when step has `minutes`.
- No bottom tab bar in cook mode.

### 4. Chrome borrowed from Groceries (no shopping feature)

- Circular checkbox → `CheckableRow` for cook completion / any checklist UI.
- Item name + optional italic note + **coral quantity** on second line.
- Strikethrough + muted ink when checked.

### 5. Chrome borrowed from Calendar (no meal-plan feature)

- Large day numeral + stacked DAY / MONTH caps.
- Coral “today” numeral and `+` affordance styling reusable for add actions.

### 6. Bottom tab bar (phone)

White bar, five-slot visual rhythm. Basil v1 uses three destinations mapped into the same visual language:

| Slot | Basil |
|---|---|
| Recipes | Recipes (active coral box icon + label) |
| Import | Import (compass / link icon) |
| Account | Account (person icon) |

Active = coral icon + coral label. Inactive = `inkFaint`. No filled pill behind the tab. Icon above label, ~10–11sp label.

---

## Anti-patterns (do not ship)

- Emoji as navigation icons
- Bordered Material cards for every list row
- Grid of image-top cards as the phone recipe library (use list rows)
- Purple / indigo accents, glassmorphism, multi-layer shadows
- Dense chip rows crowding the first viewport of Recipes
- Cook mode as a plain white `StepCard` stack
- Literal `Color(0x…)` outside `:ui` theme tokens
