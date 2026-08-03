# Basil — Implementation Specification (Firebase + Coral design revision)

> This revises the original Supabase-backed spec: (1) backend moves to Firebase — native
> SDKs on Android/iOS, Firebase REST APIs via Ktor on desktop/web, since Firebase ships no
> official KMP SDK covering those two targets; (2) the visual design is retuned to closely
> match the coral/white reference screenshots. Everything not called out below is unchanged
> from the original decisions and is not being relitigated.

## Context

Basil is a new Compose Multiplatform recipe app targeting **Android, iOS, desktop (JVM) and web (wasmJs)** from a single codebase.

The problem it solves: recipes people care about are scattered across websites, cookbooks and screenshots, and every existing option either locks them to one platform or forces an account before you can save anything. Basil captures a recipe from any of those sources, stores it locally first, and syncs it to an account only when the user wants one.

The intended outcome is an elegant, simple, image-forward recipe library with two primary surfaces — a **Recipes** page (list/grid) and an **Import** page (paste or browse a URL, extract the recipe) — plus manual entry, mobile camera scanning, and a hands-free cook mode.

Architecture guidance is taken from [j-roskopf/Phoebe](https://github.com/j-roskopf/Phoebe): Gradle convention plugins in `build-logic`, Metro DI, layered `core`/`domain`/`data`/`feature`/`ui`/`navigation` modules.

### Decisions already made (do not relitigate)

| # | Decision | Choice |
|---|---|---|
| 1 | Backend | **Firebase** — native Firebase SDKs (Auth, Firestore, Cloud Storage, Cloud Functions) on **Android/iOS**; Firebase REST APIs (Identity Toolkit, Firestore REST, Storage JSON API) via the existing Ktor client on **desktop/web**, since Firebase has no official KMP SDK spanning JVM desktop and wasmJs |
| 2 | Visual identity | Coral/white palette matched closely to the reference screenshots (warm coral primary, white cards, near-black ink text); desktop/web and mobile differ in *layout*, not colour. **Note:** the sourced app icon (green basil leaf, `branding/`) is unchanged and now diverges from the UI chrome colour — flagged as an open question, not resolved here |
| 3 | URL extraction | Firebase **Cloud Function** (2nd gen, Node.js/TypeScript) for all platforms (web cannot scrape client-side — CORS) |
| 4 | Local mode | **Anonymous Firebase Auth session from launch** (`signInAnonymously`), upgraded in place on sign-in |
| 5 | Offline first launch | Deferred session: device-local owner id, one-time rewrite when the anonymous session is created |
| 6 | Scanning | **On-device OCR, mobile only** — ML Kit (Android), Vision framework (iOS). No LLM, no per-scan cost |
| 7 | Camera | Live capture on Android/iOS only; desktop/web have no scan path |
| 8 | Browser page | **Real WebView on mobile**, URL paste field on desktop/web |
| 9 | Ingredients | Plain strings (no quantity parsing, no scaling, no shopping list) |
| 10 | Steps | `{ text, minutes? }` — minutes best-effort parsed, always user-editable |
| 11 | Images | Blob outbox → Firebase Cloud Storage; **every** image re-hosted, including imported ones. Access via Storage Security Rules (owner-scoped path) + revocable download tokens — Firebase client SDKs don't offer short-lived signed URLs without an Admin-SDK-backed Cloud Function |
| 12 | Stack | Phoebe's as-is: Kotlin 2.4 / CMP 1.11.1 / Metro / Nav3 / SQLDelight / Ktor / Coil. `supabase-kmp` and `supabase-sync-sqldelight` are dropped — no Firebase equivalent exists, so sync is a custom SQLDelight outbox engine (§6.1) |
| 13 | Modules | Feature-per-area with layered core (~16 modules). `core:network` now hosts the Firebase REST client (Identity Toolkit, Firestore, Storage JSON API bindings) instead of a Supabase client wrapper |
| 14 | Navigation | Adaptive shell, 3 destinations (Recipes / Import / Account) |
| 15 | Schema | Single `recipes` **Firestore subcollection** (`users/{uid}/recipes/{recipeId}`) with array/map fields, replacing the Postgres table + JSONB |
| 16 | Email auth | Password, plus a **bespoke 6-digit OTP** built on two Cloud Functions + Firestore (no deep links). Firebase Auth has no built-in code-based email verification — only password and deep-link "email link" sign-in — so this piece is custom infrastructure, not an out-of-the-box Firebase feature |
| 17 | Google auth | All four platforms — native SDKs on Android/iOS, hand-rolled OAuth2 + REST on desktop/web |
| 18 | Account merge | Link if identity free (`linkWithCredential` / custom-token upgrade), else sign in + offer merge |
| 19 | Sync | Pull on foreground + push on write. **No Firestore realtime listeners** (`onSnapshot` is deliberately not used, even though Firestore supports it) — same pull/push cadence as the original plan |
| 20 | Fonts / theme | Fraunces (display) + Inter (body) — both OFL, redistributable — retuned in scale/weight to visually match the reference screenshots; light **and** dark |
| 21 | v1 scope | Core + **cook mode**. The Groceries/Calendar screens in the reference screenshots are out of v1 scope (decisions #9, #12-below) — only their visual chrome is borrowed, not the features |
| 22 | Testing | `commonTest` unit tests + Roborazzi desktop screenshots + **Vitest** tests for Cloud Functions (was Deno) |
| 23 | Release | **Full signed release pipeline** on tag |
| 24 | Identity | `com.joetr.basil`, web at `basil.joetr.com` |
| 25 | Hardening | Cloud Function: Firebase Auth ID token required (anonymous qualifies, verified automatically by `onCall`) + per-user rate limit (Firestore-backed counter) + SSRF address blocking |

### Verified environment facts

- JDK 21 (Corretto), Xcode 26.6, Android SDK at `~/Library/Android/sdk`, Node 26.4 — all present.
- **CocoaPods is not installed.** Firebase's iOS SDK and GoogleSignIn-iOS have both shipped via **Swift Package Manager** since ~2021, so native Firebase on iOS still needs no CocoaPods step.
- Firebase Auth REST (Identity Toolkit) and Firestore REST/Storage JSON API are plain HTTPS + JSON — no SDK-specific toolchain constraint for desktop/web beyond the existing Ktor client.
- `jvmTarget = 17` (JDK 21 toolchain) stays as the project baseline; it was already required independent of the backend choice.
- Google Sign-In: Credential Manager on Android and GoogleSignIn-iOS on iOS both implement **native** sign-in; desktop/web use OAuth-via-browser same as before.

---

## 1. Repository layout

```
Basil/
├── build-logic/convention/          # Gradle convention plugins (see §2)
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── androidApp/                      # Android entry point, manifest, CameraX, ML Kit, google-services.json
├── iosApp/                          # Xcode project, SwiftUI host, Vision OCR bridge, GoogleService-Info.plist
├── composeApp/                      # Shared app entry: DI graph root, App(), platform mains
│   └── src/{commonMain,androidMain,iosMain,jvmMain,wasmJsMain}
├── navigation/                      # Nav3 keys, back stack, adaptive shell
├── ui/                              # Design system: theme, tokens, components, fonts
├── domain/                          # Pure Kotlin models + use cases (no Android/Compose deps)
├── data/
│   ├── recipe/                      # RecipeRepository, sync orchestration, outbox
│   ├── auth/                        # SessionRepository, anon→real upgrade, merge
│   └── image/                       # Image outbox, resize, Storage upload
├── core/
│   ├── database/                    # SQLDelight schema + per-platform drivers
│   ├── network/                     # Ktor client, Firebase REST client (Identity Toolkit,
│   │                                #   Firestore, Storage JSON API), Cloud Function bindings
│   └── platform/                    # expect/actual: camera, OCR, file picker, share,
│                                    #   browser-open, connectivity, keep-screen-on, clock
├── feature/
│   ├── recipes/                     # List/grid + detail
│   ├── cook/                        # Cook mode
│   ├── editor/                      # Manual create/edit + import review screen
│   ├── import/                      # URL paste, WebView browser, import history
│   ├── scan/                        # Camera capture → OCR → review (Android/iOS only)
│   ├── auth/                        # Sign in / sign up / OTP
│   └── settings/                    # Account, theme, sync status, about
├── firebase/
│   ├── firestore.rules
│   ├── firestore.indexes.json
│   ├── storage.rules
│   └── functions/
│       ├── extractRecipe/           # Node.js/TS Cloud Function + Vitest tests + HTML fixtures
│       └── emailOtp/                # requestEmailOtp + verifyEmailOtp callable functions
├── branding/                        # Source icon + generated platform icon sets
└── .github/workflows/               # ci.yml, release.yml, deploy-web.yml
```

**Dependency rule** (enforced by convention plugins): `feature:*` → `domain` + `ui` + `navigation`; `data:*` → `domain` + `core:*`; `domain` depends on nothing but kotlinx. Features never depend on each other or on `data:*` implementations — only on interfaces declared in `domain`.

---

## 2. Convention plugins (`build-logic/convention`)

Mirror Phoebe's set, renamed `basil.*`:

| Plugin | Applies |
|---|---|
| `basil.kmp.library` | KMP targets (android, iosX64/Arm64/SimulatorArm64, jvm, wasmJs), Kotlin 2.4 opts, `jvmTarget = 17`, explicit API mode, common test deps |
| `basil.compose.library` | `basil.kmp.library` + Compose MP plugin, compose resources, compose compiler metrics |
| `basil.feature` | `basil.compose.library` + `basil.metro` + auto-deps on `:domain`, `:ui`, `:navigation`, `:core:platform` |
| `basil.data` | `basil.kmp.library` + `basil.metro` + `:domain`, `:core:network`, `:core:database`, kotlinx-serialization |
| `basil.domain` | `basil.kmp.library` only, no Compose, no Android — enforces purity |
| `basil.ui` | `basil.compose.library` + Material3 + Coil |
| `basil.metro` | Metro compiler plugin + graph annotations |
| `basil.sqldelight` | SQLDelight plugin, DB name `BasilDatabase`, package `com.joetr.basil.db`, per-target driver deps |

Version catalog pins (from Phoebe, verified current): Kotlin `2.4.0`, CMP `1.11.1`, Material3 `1.11.0-alpha07`, AGP `9.2.1`, Metro `1.2.0`, Nav3 UI `1.1.1`, SQLDelight `2.2.1`, Ktor `3.3.2`, Coil `3.5.0`, kotlinx-serialization `1.9.0`.

**Firebase-specific additions** (replacing the `supabase-kmp` row):
- `com.google.firebase:firebase-bom` (Android, latest 33.x) → `firebase-auth-ktx`, `firebase-firestore-ktx`, `firebase-storage-ktx`, `firebase-functions-ktx`
- `androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth` + `com.google.android.libraries.identity.googleid:googleid` (Android Google sign-in)
- iOS: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, `FirebaseFunctions`, `GoogleSignIn` — added via Xcode's SPM package manager in `iosApp.xcodeproj`, **not** the Gradle version catalog (Kotlin/Native has no first-class Firebase iOS binding; these are consumed from the Swift host side and bridged via `expect/actual` where Kotlin needs them)
- No Firebase dependency exists for `jvm`/`wasmJs` targets — those talk to Identity Toolkit, Firestore REST, and the Storage JSON API purely over the existing Ktor client

---

## 3. Design system (`:ui`)

### Palette (matched closely to the reference screenshots)

| Token | Light | Dark | Use |
|---|---|---|---|
| `primary` (coral) | `#E8735F` | `#F0876F` | CTAs, active nav, checkmarks, cook-mode full-bleed background |
| `onPrimary` | `#FFFFFF` | `#241512` | Text/icons on filled coral |
| `ink` | `#2B2B2E` | `#EDEEF0` | All primary text |
| `inkMuted` | `#8C8C90` | `#A7A7AE` | Meta rows, captions, timestamps |
| `canvas` | `#FFFFFF` | `#17181C` | App background |
| `surface` | `#FFFFFF` | `#201F24` | Cards, sheets, sidebar |
| `surfaceTint` | `#FBEFEC` | `#2A2226` | Chips, inactive fields — a faint coral wash, not grey |
| `outline` | `#EDEAE7` | `#302E33` | Hairlines, card borders |
| `accentWarm` | `#F2A33C` | `#F2A33C` | Sparingly: cook-mode timers, duration pills — kept distinct from brand coral for legibility |
| `error` | `#B3261E` | `#E8756B` | Deliberately a different hue/depth from `primary` since primary itself is coral-red |

No pure black, no pure white in dark mode. **Every** colour goes through these semantic tokens — literal `Color(0x…)` in a feature module is a review failure.

**Open flag:** the sourced app icon (`branding/`, green basil leaf) no longer derives from this palette the way the original spec intended (decision #2 originally said "one palette from the app icon"). This spec proceeds with the coral system per explicit instruction to match the screenshots closely; reconciling the icon and the in-app palette is left as a follow-up decision, not resolved here.

### Typography

Bundled OFL fonts via `Res.font` in `:ui` — kept as Fraunces + Inter (both open-license and already selected) rather than attempting to match a possibly-proprietary reference typeface, but **retuned** to mirror the screenshots' proportions:

- **Fraunces** (variable, `opsz` mid, `SOFT` slight — less dramatic than a pure display cut) — `displayLarge` 32/36, `displayMedium` 26/30, `titleLarge` 22/26. Recipe titles ("Torta di mele") and page titles ("Groceries"-equivalent, e.g. "Recipes").
- **Inter** — `bodyLarge` 16/24, `bodyMedium` 14/20, `labelMedium` 12/16 (uppercase, +0.6 tracking, ExtraBold) for section/marketing-style headers and meta labels, `labelSmall` 11/14. Everything else, including all numerals and ingredient quantities (shown in `primary` coral, matching the reference's red quantity lines).

### Shape & elevation

Radii: `card` 20dp, `sheet` 28dp, `chip`/`pill` 999dp, `field` 14dp, `image` 16dp. Shadows are soft and low-contrast (`4dp` blur, 6% ink) — no Material default elevation overlays. Spacing scale `4/8/12/16/24/32/48`.

### Components to build

`BasilCard`, `RecipeGridCard` (image-dominant, 4:3), `RecipeListRow` (leading circular 72dp image, coral quantity/meta line underneath the title — matches the reference's list-row pattern), `MetaRow` (icon + label + value triples: servings / time / source), `PillButton` (filled coral + tonal), `Chip` / `ChipRow`, `SearchField`, `SectionHeader` (Inter ExtraBold uppercase, matching the reference's "YOUR RECIPES"-style headers), `EmptyState`, `StepCard` (numbered badge, optional duration pill, optional image, "Mark as complete"), `CheckableRow` (circular checkbox that fills solid coral with a white check on completion — reused from the reference's list-item checkbox style for cook-mode step completion, **not** for a shopping list, which stays out of scope), `ImagePlaceholder` (basil-leaf motif), `SyncStatusBadge`, `AdaptiveScaffold`.

### Layout language per form factor

Both derive from the same tokens; only structure differs.

- **≥840dp (desktop/web)** — persistent 260dp left sidebar on `surface` with the Basil mark, nav items (icon + label, active item in `primary` coral), and account chip pinned at the bottom. Content area on `canvas`: Fraunces page title, a subtitle paragraph, a right-aligned `PillButton` for the primary action, filter/sort controls, then the recipe collection. Grid is 3–4 columns, list rows use the leading circular image + `MetaRow` + "Read more →". Max content width 1280dp, centred.
- **600–840dp** — navigation rail (icons + short labels), 2-column grid.
- **<600dp (phones)** — bottom navigation bar (white bar, coral active icon + label — matching the reference's tab bar), single-column. Canvas is plain white (matching the reference more closely than a gradient); content is a stack of 20dp-radius `surface` cards with generous 24dp gutters. Recipe cards: full-width image top, Fraunces title, small icon meta row (clock + tag icons), 1–2 line description snippet — this maps directly onto the reference's "All" recipe list screenshot. Recipe detail: full-width hero image with rounded bottom corners, Fraunces title below, a row of pill-style meta chips (servings/time/adjust icons), ingredient list with the quantity shown on its own line in `primary` coral underneath each ingredient name.
- **Cook mode (all mobile sizes)** — full-bleed `primary` coral background (not `canvas`). Current step at full opacity in `onPrimary` text; the step immediately before and after are shown faded (~40% opacity) peeking above/below, exactly as in the reference's cooking-mode screenshot. A white circular badge in the top area shows the current step number. Close (X) top-left, timer icon top-right when the step has `minutes`. A slim progress track or dot row sits at the bottom instead of the reference's page-dot indicator, which reads better at a glance during hands-free use.

### App icon

Source `/Users/joer/Downloads/basil.png` → `branding/` (already generated in the repo). Generate: Android adaptive icon (green-tinted `surfaceTint` background layer + leaves foreground, plus monochrome layer for themed icons), iOS `AppIcon.appiconset` (all sizes, opaque `canvas` background — no alpha), macOS/Windows/Linux desktop icons (`.icns`, `.ico`, `.png`), and web `favicon.ico` + 192/512 PNGs + `manifest.json`.

**README must include:** `Basil icon by [Freepik](https://www.flaticon.com/free-icon/basil_8154210) from Flaticon.`

---

## 4. Domain model (`:domain`)

Unchanged from the original spec — Firestore stores these fields natively (arrays and maps map directly onto `ingredients: List<String>` and `steps: List<RecipeStep>`), so no model changes were needed for the backend swap.

```kotlin
data class Recipe(
    val id: String,                    // UUID v4, client-generated — also used as the Firestore document id
    val ownerId: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,             // remote Storage download URL once uploaded
    val localImageId: String?,         // set while a blob is pending upload
    val sourceUrl: String?,            // origin site, kept for attribution
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<String>,     // plain strings, order significant
    val steps: List<RecipeStep>,
    val tags: List<String>,
    val notes: String?,
    val isFavourite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,               // server-authoritative; local writes set 0 sentinel
    val deleted: Boolean,
)

data class RecipeStep(val text: String, val minutes: Int?)

enum class RecipeSource { MANUAL, IMPORT, SCAN }
enum class ExtractionConfidence { FULL, PARTIAL, NONE }

data class ExtractedRecipe(          // returned by the Cloud Function and by OCR
    val confidence: ExtractionConfidence,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val sourceUrl: String?,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<String>,
    val steps: List<RecipeStep>,
    val tags: List<String>,
    val rawText: String?,             // page/OCR text shown alongside the editor
)
```

Use cases: `ObserveRecipes(query, tags, sort)`, `ObserveRecipe(id)`, `SaveRecipe`, `DeleteRecipe`, `ToggleFavourite`, `ImportRecipeFromUrl`, `ScanRecipeFromImage`, `SyncNow`, `ObserveSession`, `SignIn*`, `SignOut`, `MergeLocalIntoAccount`.

`ImportRecipeFromUrl` and `ScanRecipeFromImage` both return `ExtractedRecipe` — the editor consumes one type regardless of source.

---

## 5. Firebase backend

### 5.1 Data model (`firebase/firestore.rules`, `firebase/firestore.indexes.json`)

Firestore subcollection per user, so ownership is enforced by path rather than a row-level policy:

```
users/{uid}/recipes/{recipeId}
  title            string
  description      string | null
  imageUrl         string | null
  sourceUrl        string | null
  servings         number | null
  prepMinutes      number | null
  cookMinutes      number | null
  ingredients      array<string>
  steps            array<map{ text: string, minutes: number | null }>
  tags             array<string>
  notes            string | null
  isFavourite      boolean
  createdAt        number   // epoch ms, client-set on create
  updatedAt        number   // epoch ms — see below on how this stays server-authoritative
  deleted          boolean
```

**`updatedAt` must stay server-authoritative**, matching the original Postgres trigger's guarantee (the sync cursor and LWW resolution depend on it):
- Native SDK writes (Android/iOS) set the field to `FieldValue.serverTimestamp()`.
- REST writes (desktop/web) use a Firestore `commit` request with a `transform` write: `fieldTransforms: [{ fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" }]` alongside the `update`/`set` write in the same request — the REST API supports this natively, so no Cloud Function round-trip is needed just to stamp the timestamp.

```
// firestore.rules (sketch)
match /users/{uid}/recipes/{recipeId} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
match /users/{uid}/importEvents/{eventId} {
  allow read: if request.auth != null && request.auth.uid == uid;
  allow write: if false; // written only by the Cloud Function via the Admin SDK
}
match /otpCodes/{email} {
  allow read, write: if false; // written only by the Cloud Functions via the Admin SDK
}
```

Index: single-field ascending index on `updatedAt` within the `recipes` subcollection (Firestore creates this automatically); the pull query is `orderBy("updatedAt").orderBy("__name__").startAfter(cursor).limit(pageSize)` — no composite index is required since there's no additional `where` clause (the path already scopes to the owner).

**Storage** (`firebase/storage.rules`): bucket path `users/{uid}/{recipeId}.jpg`.

```
match /users/{uid}/{allPaths=**} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

Reads use Firebase's standard revocable **download-token URL** (`getDownloadURL()` natively, or read the `downloadTokens` field from the object's metadata via the Storage JSON API on desktop/web and construct `https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{encodedPath}?alt=media&token={token}`). This is a deliberate deviation from the original "1h TTL signed URL" plan: Firebase's client-obtainable URLs aren't time-boxed, only revocable by rotating the token. If a hard TTL becomes a real requirement later, add a Cloud Function that mints Admin-SDK V4 signed URLs on demand — not needed for v1 since the bucket itself stays private and gated by Security Rules.

**Auth settings:** enable Anonymous provider; enable Email/Password provider (used underneath the custom OTP flow, see §5.2); enable Google provider; add `basil.joetr.com` to authorized domains; register the reversed-client-id URL scheme in iOS `Info.plist` for GoogleSignIn-iOS's redirect (standard for that SDK, unrelated to the "no deep links" OTP constraint below).

### 5.2 Cloud Functions (`firebase/functions/`, Node.js/TypeScript, 2nd gen)

**`extractRecipe`** — `onCall` function, `{ url: string } → ExtractedRecipe`. `onCall` verifies the caller's Firebase Auth ID token automatically (anonymous sessions qualify), so step 1 of the original pipeline is handled by the platform rather than hand-checked.

Pipeline:
1. ~~Authorise~~ — handled by `onCall`'s built-in auth context; reject if `context.auth` is absent.
2. **Rate limit** — count this user's documents in `users/{uid}/importEvents` created in the last hour (Admin SDK Firestore query, `where('createdAt', '>', oneHourAgo)`); >30 → `resource-exhausted`. Write an event on success.
3. **Validate the URL** — scheme must be `http`/`https`; resolve DNS and reject private/loopback/link-local/CGNAT ranges and `169.254.169.254`; re-validate on every redirect (max 3); reject non-HTML content types.
4. **Fetch** — 10s timeout, 2MB response cap, desktop browser User-Agent.
5. **Parse, in order:**
   - `<script type="application/ld+json">` → `Recipe` (or `@graph` containing one). Handle `recipeIngredient`, `recipeInstructions` (string | array of strings | `HowToStep` | `HowToSection`), `totalTime`/`prepTime`/`cookTime` (ISO-8601 durations), `recipeYield`, `image` (string | array | `ImageObject`). → `confidence: FULL`.
   - Microdata / RDFa (`itemprop="recipeIngredient"` etc.). → `FULL`.
   - Heuristic: find `<ul>`/`<ol>` blocks under headings matching `/ingredient/i` and `/instruction|method|direction|step/i`. → `PARTIAL` if both found, else `NONE`.
6. **Always populate** `title` (`og:title` → `<title>`), `imageUrl` (`og:image`), `sourceUrl`, and `rawText` (readable text, ~20KB cap), whatever the confidence.
7. Parse per-step `minutes` from step text with the same rules as the Kotlin parser (see §6.3) so behaviour matches OCR imports.

**`requestEmailOtp`** and **`verifyEmailOtp`** — `onCall` functions implementing the bespoke code-based email verification Firebase doesn't provide natively:
- `requestEmailOtp({ email })` — generates a 6-digit code, stores its SHA-256 hash + a 10-minute expiry + an attempt counter in `otpCodes/{email}` via the Admin SDK, and sends it by writing a document to the `mail` collection watched by the **Trigger Email** Firebase Extension (lowest-lift path; swap for a direct SendGrid/Resend call later if the extension's deliverability isn't sufficient).
- `verifyEmailOtp({ email, code })` — checks hash + expiry + attempt limit. If the caller is still anonymous and no Auth user exists for `email`, this **is** the upgrade: call `getAuth().updateUser(callerUid, { email, emailVerified: true })` on the existing anonymous uid (no separate `linkWithCredential` round-trip needed) and return a fresh custom token via `getAuth().createCustomToken(callerUid)`. If an Auth user already exists for `email` under a *different* uid, return `already-exists` — the client falls into the same merge-prompt flow as Google (§6.2), signing in via a custom token minted for the existing uid and offering to migrate local rows.

Secrets: both function groups use `FIREBASE_SERVICE_ACCOUNT`-equivalent Admin SDK credentials from the Cloud Functions runtime environment (Secret Manager) — never shipped to clients, mirroring the original service-role-key note.

Tests (Vitest, reusing the fixture set already in the repo at `supabase/functions/extract-recipe/fixtures/*`, moved to `firebase/functions/extractRecipe/fixtures/`): the same ≥12 real-recipe-site HTML fixtures (JSON-LD, microdata, `@graph`, `HowToSection`, no-markup blog, redirect chain), plus SSRF cases (`http://127.0.0.1`, `http://169.254.169.254`, `file://`, a public host redirecting to localhost), plus OTP hash/expiry/attempt-limit unit tests against a mocked Admin SDK. Optionally run integration tests against the Firebase Local Emulator Suite (Firestore + Auth + Functions).

---

## 6. Client architecture

### 6.1 Sync (`:core:database` + `:data:recipe`)

SQLDelight is the **single source of truth on all four platforms** — no Firestore native offline cache is used, even on the platforms where one exists, so desktop (which has no Firestore SDK at all) behaves identically to the rest. The UI only ever observes SQLDelight queries; nothing in a feature module awaits the network.

Since no `supabase-sync-sqldelight` equivalent exists for Firebase, this is a **custom sync engine**:

- Local `recipes` table mirrors the Firestore document fields exactly, plus `pending_sync INTEGER` and `local_image_id TEXT`.
- `RecipeOutboxDao` records pending create/update/delete rows keyed by recipe id, written in the same transaction as the optimistic local write.
- **Push:**
  - Android/iOS — native SDK batched writes (`Firestore.batch()` / `WriteBatch`) upserting each outbox row's document by `id`; idempotent because it's a full-document `set()`.
  - Desktop/web — Firestore REST `commit` endpoint, one `write` per outbox row (`update` with `currentDocument` unset for idempotent upsert), `Authorization: Bearer <idToken>` from the current session.
  - Clear the outbox entry only after a confirmed success response.
- **Pull:** one-shot queries only — **never** `addSnapshotListener` / a REST streaming endpoint, to honour the "no realtime websocket" decision even though Firestore supports it.
  - Android/iOS — `collection.orderBy("updatedAt").orderBy(FieldPath.documentId()).startAfter(cursor).limit(pageSize).get()`.
  - Desktop/web — Firestore REST `runQuery` with the equivalent `orderBy`/`startAt`/`limit` structure.
- **Deletes:** soft — set `deleted = true`. Queries filter it out. Also delete the Storage object.
- **Token refresh:** Firebase ID tokens expire hourly. Native SDKs auto-refresh transparently; the desktop/web REST client must refresh manually via `securetoken.googleapis.com/v1/token` using the stored refresh token, either proactively before each sync cycle or reactively on a `401`.
- **Triggers:** app start, foreground/window-focus/tab-visible, pull-to-refresh, connectivity regained, and immediately after any local write.
- **Conflicts:** whole-row last-write-wins on the server's `updatedAt`. Acceptable because a recipe is edited as a unit. Never model a field as a relative operation.
- **Known limits to surface in the UI:** a rejected row blocks its table's outbox. Show a `SyncStatusBadge` with `Synced / Syncing / N pending / Error` and a "Retry" action in Settings that can drop a poisoned outbox entry.

Drivers: `AndroidSqliteDriver`, `NativeSqliteDriver` (iOS), `JdbcSqliteDriver` on a file under the app data dir (desktop), **SQLDelight web-worker driver** (wasmJs, OPFS-backed) — the wasm worker JS must be included in the webpack config, as Phoebe does.

### 6.2 Session (`:data:auth`)

Same state machine as the original plan, now backed by Firebase:

```
LocalPending(deviceOwnerId)  → app fully usable offline; recipes written with a local UUID owner
      │ network available
      ▼
Anonymous(userId)            → signInAnonymously(); one-time owner-id rewrite (LocalPending id → userId), sync starts
      │ sign in / sign up
      ▼
Authenticated(userId, email) → normal operation
```

**Upgrade path on sign-in from `Anonymous`:**
1. **Google** — try `currentUser.linkWithCredential(GoogleAuthProvider.credential(idToken))` (Android/iOS native SDK) or the REST equivalent via `accounts:signInWithIdp` with `idToken` from the OAuth2 flow (desktop/web). If it succeeds, the uid is unchanged and every row carries over with zero movement.
2. **Email** — handled entirely by `verifyEmailOtp` (§5.2): if no account exists for that email, the anonymous uid is upgraded in place server-side, no client-side link call needed.
3. **If either fails because the identity is already tied to another account** (`credential-already-in-use` for Google, `already-exists` from `verifyEmailOtp`): sign in to the existing account (via `signInWithCredential` or the custom token returned by the function), then count local rows owned by the old anonymous id. If >0, prompt *"You have N recipes from before signing in. Add them to your account?"*
   - **Yes** → assign new UUIDs, stamp `ownerId` with the real user id, dedupe against existing rows by `sourceUrl` (and by normalised title where `sourceUrl` is null), enqueue through the outbox.
   - **No** → hard-delete those local rows.
   - Either way the abandoned anonymous account is left to expire; do not attempt to delete it from the client.
4. Sign-out clears the local database and returns to a fresh `Anonymous` session.

**Google sign-in** — one `expect fun googleSignIn(): AuthResult`:
- **Android** — Credential Manager's `GoogleIdTokenCredential` via `CredentialManager.getCredential`, then `GoogleAuthProvider.credential(idToken)` + `linkWithCredential` on the native Firebase Android SDK. Generate a **fresh random nonce per attempt**.
- **iOS** — GoogleSignIn-iOS SDK (SPM) obtains an ID token, then `GoogleAuthProvider.credential(idToken:accessToken:)` + `linkWithCredential` on the native Firebase iOS SDK. Requires the reversed-client-id URL scheme in `Info.plist`.
- **Web** — hand-rolled Google OAuth2 authorization-code redirect flow to `accounts.google.com`, returning to a `basil.joetr.com` callback; exchange the resulting Google `id_token` via Identity Toolkit REST `accounts:signInWithIdp`.
- **Desktop** — bind an ephemeral loopback listener on `127.0.0.1`, open the system browser at Google's OAuth2 authorize URL with `redirect_uri=http://127.0.0.1:{port}/auth-callback`, exchange the code for an `id_token`, call `accounts:signInWithIdp`, shut the listener down. 2-minute timeout.

**Email** — password sign-up/sign-in via native SDK methods (`createUserWithEmailAndPassword`/`signInWithEmailAndPassword`) on Android/iOS, or Identity Toolkit REST (`accounts:signUp`/`accounts:signInWithPassword`) on desktop/web. **OTP verification always goes through `requestEmailOtp`/`verifyEmailOtp`** (§5.2) regardless of platform — the client calls `verifyEmailOtp` then `signInWithCustomToken` (native SDK) or REST `accounts:signInWithCustomToken` (desktop/web). No deep links anywhere in this flow.

### 6.3 Import (`:feature:import` + `:feature:editor`)

- **Desktop/web** — URL field (with paste-detect and clipboard prefill) plus a list of recently imported URLs and their outcomes.
- **Android/iOS** — a real in-app WebView (`AndroidView(WebView)` / `UIKitView(WKWebView)` behind one `expect` composable — hand-rolled, ~150 lines each, to avoid pulling KCEF's bundled Chromium in transitively). Address bar, back/forward, and a persistent **"Save this recipe"** FAB that sends the current page URL to the extractor. Intercept system back to navigate the WebView first.
- **Share targets** — Android `ACTION_SEND` intent filter for `text/plain`, iOS Share Extension. Both route straight into the import flow.
- Calls the `extractRecipe` Cloud Function — `httpsCallable("extractRecipe")` on native SDKs, or the callable-function REST convention (`POST https://{region}-{project}.cloudfunctions.net/extractRecipe` with body `{ "data": { "url": ... } }` and `Authorization: Bearer <idToken>`) on desktop/web.
- **Review screen** (shared with scan and manual entry — one editor, three entry points): pre-filled fields, editable ingredient rows and step rows (each with an optional minutes field), image preview with replace/remove, tag input, and — when `confidence != FULL` — a collapsible `rawText` panel to copy from. Import never dead-ends.
- **Step-duration parser** (`:domain`, shared with the Cloud Function's TS twin): matches `N min|minute|minutes|hr|hour|hours`, `an hour`, `half an hour`, ranges (`20–25 minutes` → take the upper bound), and `overnight` → null. Unit-tested both sides against the same case table.

### 6.4 Scan (`:feature:scan`, Android + iOS only)

Unchanged — pure on-device, no backend dependency.

The feature module and its nav destination are compiled for all targets but the destination is hidden where `ImageCapture.isAvailable` is false, so desktop/web never show a dead entry point.

- **Android** — CameraX preview + `ImageCapture`, then ML Kit Text Recognition v2 (bundled model, on-device, free).
- **iOS** — `AVCaptureSession` preview in a `UIKitView`, then `VNRecognizeTextRequest` with `.accurate` and language correction.
- Both produce raw text → shared `OcrRecipeParser` in `:domain` → `ExtractedRecipe` → the same review screen. The parser splits on an ingredients-like block (short lines, leading numerals/fractions) versus a steps-like block (sentences, imperative verbs) and takes the first non-empty line as the title.
- Unit-test the parser against a fixture set of captured OCR text from real cookbook pages and index cards.

### 6.5 Images (`:data:image`)

1. Capture or import produces bytes → downscale to max 1600px on the long edge, JPEG q80 (`expect` encoder: Bitmap on Android, `UIImage` on iOS, `ImageIO` on JVM, canvas on wasm).
2. Write to a local `images` BLOB table, set `recipes.local_image_id`, enqueue in the image outbox.
3. Worker uploads to `users/{userId}/{recipeId}.jpg` — native SDK `StorageReference.putFile`/`putData` (Android/iOS), or the Storage JSON API `POST /upload/storage/v1/b/{bucket}/o?uploadType=media&name=...` via Ktor (desktop/web) — then resolves the download-token URL, sets `imageUrl`, clears `localImageId` and drops the blob.
4. **Imported images are re-hosted the same way** — the Cloud Function returns the site's image URL, the client fetches and uploads it, so images survive site redesigns. `sourceUrl` is retained for attribution.
5. Coil 3 renders remote download-token URLs everywhere and blob-backed data while pending, via a custom `Fetcher` reading the local blob table. Since these URLs aren't time-boxed (§5.1), the fetcher only needs to re-resolve on an explicit `403` (token rotated), not on a TTL timer.

### 6.6 Cook mode (`:feature:cook`)

Full-screen, entered from recipe detail. Full-bleed `primary` coral background with the current step at full opacity and the adjacent steps faded above/below (see §3 layout language). One step at a time; swipe or tap to advance; progress dots; "Mark as complete" per step using `CheckableRow`. A countdown timer appears when the step has `minutes`, with start/pause/reset and a completion sound + haptic (Android/iOS). Screen stays awake via `expect fun keepScreenOn` (`FLAG_KEEP_SCREEN_ON`, `UIApplication.isIdleTimerDisabled`, desktop no-op, web Wake Lock API). Total time = sum of step minutes, falling back to `cookMinutes`. State is in-memory only — leaving cook mode discards progress.

### 6.7 Navigation (`:navigation`)

Single Nav3 back stack. Keys: `RecipesKey`, `RecipeDetailKey(id)`, `EditorKey(recipeId?, extracted?)`, `ImportKey`, `BrowserKey(url?)`, `ScanKey`, `CookKey(recipeId)`, `AuthKey`, `AccountKey`.

`AdaptiveScaffold` selects sidebar / rail / bottom bar on width (840dp, 600dp breakpoints) and hosts the three top-level destinations. On ≥840dp, recipe detail renders in a two-pane layout beside the list. **Web deep links**: `/recipes`, `/recipe/{id}`, `/import`, `/account` mapped bidirectionally to the back stack via the History API.

---

## 7. Configuration & secrets

`BasilConfig` is generated at build time per platform:
- **Android** — standard `google-services.json` (gitignored, per-environment, injected by CI from a base64 secret).
- **iOS** — `GoogleService-Info.plist` (gitignored, same pattern).
- **Desktop/web (REST-only)** — a plain Firebase web config object (`apiKey`, `projectId`, `authDomain`, `storageBucket`) generated from `local.properties` locally and CI environment variables in Actions. Firebase's web API key is public-safe by design, same as the original Supabase anon key — Firestore/Storage **Security Rules**, not the key, are what protect data.

The Cloud Functions' Admin SDK service-account credential exists only in the Functions runtime environment (Secret Manager) — never shipped to any client.

---

## 8. Testing

- **`commonTest`** — recipe JSON normalisation and edge cases, step-duration parser (shared case table with the TS implementation), OCR text→recipe parser against captured fixtures, sync/outbox behaviour against a fake `RemoteSource` (offline write → queue → drain → conflict → LWW resolution), and the anon→account merge including the dedupe path.
- **Roborazzi (desktop)** — screenshot baselines for every `:ui` component and each main screen, in **light and dark**, at phone/tablet/desktop widths.
- **Vitest** — Cloud Functions (`extractRecipe`, `requestEmailOtp`, `verifyEmailOtp`) against HTML fixtures, SSRF cases (§5.2), and a mocked Admin SDK; optionally run against the Firebase Local Emulator Suite for integration coverage.

---

## 9. CI & release

`.github/workflows/ci.yml` on every push/PR: `./gradlew build desktopTest verifyRoborazziDesktop wasmJsBrowserDistribution`, `npm test` (Vitest) for `firebase/functions/*`. All four targets must compile on every commit — the whole point in a KMP project is catching a wasm- or iOS-only break immediately.

`deploy-web.yml` on `main`: build the wasm distribution and deploy to `basil.joetr.com`; also `firebase deploy --only functions,firestore:rules,storage:rules`.

`release.yml` on tag `v*`:
- **Android** — signed AAB + APK (keystore from secrets).
- **Desktop** — `packageDmg` (notarised, Apple Developer ID from secrets), `packageMsi`, `packageDeb`.
- **iOS** — unsigned IPA for sideloading (Phoebe's approach; avoids Apple distribution certificates until the app is store-ready).
- Attach all artifacts to a GitHub Release.

Required secrets: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `APPLE_DEVELOPER_ID_CERT_B64`, `APPLE_CERT_PASSWORD`, `APPLE_NOTARY_*`, `FIREBASE_PROJECT_ID`, `FIREBASE_WEB_API_KEY`, `FIREBASE_SERVICE_ACCOUNT_B64`, `GOOGLE_SERVICES_JSON_B64`, `GOOGLE_SERVICE_INFO_PLIST_B64`, `BASIL_GOOGLE_WEB_CLIENT_ID`, and the web host's deploy token.

---

## 10. Build order

1. **Scaffold** — Gradle wrapper, version catalog, `settings.gradle.kts`, all convention plugins, empty modules. Gate: every target assembles.
2. **Design system** — coral/white tokens, fonts, theme, components + Roborazzi baselines. Gate: a component gallery screen renders on desktop in both themes.
3. **Local core** — SQLDelight schema, four drivers, `RecipeRepository` local-only, manual editor, recipes list/grid, detail. Gate: create and browse recipes offline on all four platforms.
4. **Navigation shell** — adaptive scaffold, three destinations, web deep links.
5. **Firebase** — Firestore rules/indexes, Storage rules, Auth provider config; `SessionRepository` with the deferred-anonymous state machine; custom sync engine wired (native SDKs on mobile, REST on desktop/web). Gate: create offline, go online, verify the document lands in Firestore and appears on a second device.
6. **Auth UI** — email + OTP (via the two Cloud Functions), Google on all four platforms, sign-out, merge-on-sign-in.
7. **Images** — resize, blob outbox, Storage upload, Coil fetcher.
8. **Import** — `extractRecipe` Cloud Function (with hardening and tests), URL flow on desktop/web, WebView + share targets on mobile, review screen.
9. **Scan** — CameraX + ML Kit, AVFoundation + Vision, OCR parser.
10. **Cook mode.**
11. **Branding & release** — icons for all platforms, README with the Flaticon attribution, CI and release workflows.

---

## 11. Verification

**Per-platform smoke run:**
- Android: `./gradlew :androidApp:installDebug`
- Desktop: `./gradlew :composeApp:run`
- Web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- iOS: open `iosApp/iosApp.xcodeproj`, run on a simulator

**End-to-end scenarios that must pass before calling v1 done:**
1. **Offline first launch** — airplane mode, fresh install: app opens, create a recipe with a photo, quit and reopen; the recipe is there. Restore network; within one foreground cycle it appears in Firestore and the image lands in Cloud Storage.
2. **Cross-device sync** — sign in on desktop and phone with the same account; add a recipe on desktop, foreground the phone, it appears. Delete on the phone, foreground desktop, it disappears.
3. **Anonymous merge** — use the app anonymously on desktop (3 recipes), sign into a Google account that already has recipes on the phone; take the merge prompt; end state is the union, no duplicates, both devices agree.
4. **Import, all confidences** — a JSON-LD site (`FULL`, fully pre-filled), a microdata site (`FULL`), a markup-free personal blog (`PARTIAL`/`NONE`, editor opens pre-filled with title/image/raw text). Verify on all four platforms, including that web works despite CORS.
5. **Hardening** — call `extractRecipe` with no auth (unauthenticated `onCall` rejection), 31 times in an hour (rate-limited), with `http://127.0.0.1:8000` and a public URL that redirects to localhost (both rejected).
6. **Scan** — photograph a cookbook page on Android and iOS; the review screen is pre-filled and correctable; the saved recipe syncs.
7. **Cook mode** — start a recipe with per-step minutes; timer counts down, screen stays awake, progress advances, previous/next step preview fades correctly.
8. **Responsive** — resize the desktop window from 1400dp to 500dp: sidebar → rail → bottom bar with no layout breakage or state loss.
9. **Theme** — toggle system dark mode on every platform; no unstyled surfaces, no illegible text.

---

## 12. Explicitly out of scope for v1

Shopping lists, meal planning, ingredient scaling and unit conversion (blocked by the plain-string ingredient decision), recipe sharing/public links, collaborative editing, nutrition data, desktop and web camera capture, Firestore realtime listeners, and any LLM-backed extraction.

**Note on the reference screenshots:** two of them (the checklist "Groceries" view stored in Reminders, and the "Calendar"/meal-plan view) depict a shopping-list and meal-planning feature set. Those features remain out of scope per the line above — only their visual chrome (the circular checkbox pattern reused in `CheckableRow`, the coral quantity/meta-line typography, the bottom nav bar styling) was extracted into the design system in §3, not the features themselves.
