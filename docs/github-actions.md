# GitHub Actions

Basil CI deploys the web app to GitHub Pages and the Firebase backend (Cloud Functions, Firestore rules/indexes, Storage rules) on every push to `main`.

Production web URL: [https://basil.joetr.com/](https://basil.joetr.com/)

## Workflows

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci.yml` | push/PR to `main` | Full Gradle build, desktop tests, Roborazzi, wasm distribution, Cloud Function unit tests, iOS simulator compile |
| `deploy-web.yml` | push to `main` | Deploy Firebase backend + build wasm + publish GitHub Pages |
| `release.yml` | push tag `v*` | Package desktop installers, Android AAB, wasm; create GitHub Release |

Merging to `main` always deploys Firebase and the production web build. Tag pushes create native release artifacts separately.

## Firebase backend deploy

The **Deploy Web** workflow runs `scripts/deploy-firebase-backend.sh --smoke-test`, which:

1. Installs and tests Cloud Functions (`npm ci`, `npm test`, `npm run build`)
2. Deploys:
   - **Cloud Functions** — `extractRecipe`, `proxyImage`, `requestEmailOtp`, `verifyEmailOtp`
   - **Firestore** — security rules and composite indexes
   - **Storage** — security rules
3. Smoke-tests `proxyImage` over HTTPS

Deploy locally with the same script:

```bash
firebase login
./scripts/deploy-firebase-backend.sh --smoke-test
```

CI uses a deploy token instead of interactive login:

```bash
firebase login:ci
```

Add the printed token as the `FIREBASE_TOKEN` repository secret.

### Firebase project

Default project: `basil-dffbd` (see `firebase/.firebaserc`).

Override for a one-off deploy:

```bash
FIREBASE_PROJECT_ID=basil-dffbd ./scripts/deploy-firebase-backend.sh
```

## GitHub Pages

Production uses this repository's GitHub Pages site:

- URL: `https://basil.joetr.com/`
- Source: GitHub Actions
- Custom domain: `basil.joetr.com`
- Artifact path: `composeApp/build/dist/wasmJs/productionExecutable/`

### Pages setup

1. Go to **Settings → Pages**.
2. Set **Build and deployment** source to **GitHub Actions**.
3. Set **Custom domain** to `basil.joetr.com`.
4. Enable **Enforce HTTPS** once GitHub provisions the certificate.

### DNS

Configure at your `joetr.com` DNS provider:

```text
basil  CNAME  j-roskopf.github.io
```

Do not keep conflicting `A`, `AAAA`, or `CNAME` records for `basil` pointing elsewhere.

Verify:

```sh
dig +short basil.joetr.com
```

## Repository secrets

### Required for deploy-web (main branch)

| Secret | Purpose |
|--------|---------|
| `FIREBASE_TOKEN` | `firebase login:ci` token for deploy |
| `FIREBASE_PROJECT_ID` | Firebase project id (`basil-dffbd`) |
| `FIREBASE_WEB_API_KEY` | Web API key baked into wasm release builds |
| `BASIL_GOOGLE_WEB_CLIENT_ID` | Google OAuth web client id for wasm builds |

Optional (recommended — avoids relying on defaults in `generateBasilConfig`):

| Secret | Purpose |
|--------|---------|
| `FIREBASE_AUTH_DOMAIN` | e.g. `basil-dffbd.firebaseapp.com` |
| `FIREBASE_STORAGE_BUCKET` | e.g. `basil-dffbd.firebasestorage.app` |
| `FIREBASE_FUNCTIONS_REGION` | Default `us-central1` |
| `BASIL_GOOGLE_WEB_CLIENT_SECRET` | Desktop/wasm OAuth code exchange (see security note below) |
| `GOOGLE_SERVICES_JSON_B64` | Base64-encoded `androidApp/google-services.json` from Firebase (recommended for release builds) |

### Required for release tags (`v*`)

| Secret | Purpose |
|--------|---------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release `.jks` / `.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Plus the Firebase/Google secrets above for signed release builds that embed config.

### Desktop signing (optional, deferred)

- **Windows MSI** — built unsigned on `windows-latest`. No Azure Artifact Signing secrets are required. Windows SmartScreen may warn on first install until you add signing later.
- **macOS DMG** — currently unsigned. Add Apple Developer ID + notarization secrets when you want a signed DMG.
- **Linux DEB** — unsigned (normal for `.deb` packages).

When you are ready for signed macOS/Windows releases, follow the same pattern as Phoebe's [release-signing-setup.md](https://github.com/j-roskopf/Phoebe/blob/main/docs/release-signing-setup.md).

## Firebase console checklist

Before the first deploy succeeds end-to-end:

1. **Authentication → Sign-in method**: enable Anonymous, Email/Password, Google.
2. **Authentication → Settings → Authorized domains**: add `basil.joetr.com` and `localhost`.
3. **Project settings → Your apps**:
   - Android (`com.joetr.basil`) → `androidApp/google-services.json`
   - iOS (`com.joetr.basil`) → `iosApp/iosApp/GoogleService-Info.plist`
   - Web → API key and auth domain for CI secrets
4. **Android Google Sign-In fingerprints** (required for release APK/AAB):
   - Debug: `./gradlew :androidApp:signingReport` → add the **debug** SHA-1 under the Android app in Firebase.
   - Release: add the **release** keystore SHA-1 as well (CI prints it in the `Print release signing SHA-1` step on tagged releases).
   - After adding fingerprints, download a fresh `google-services.json` and set `GOOGLE_SERVICES_JSON_B64` for release CI (or update your local copy).

### Google OAuth redirect URIs

In [Google Cloud Console](https://console.cloud.google.com/) → **APIs & Services → Credentials** → your **Web client**:

| Platform | Redirect URI |
|----------|--------------|
| Desktop | `http://127.0.0.1:3847/auth-callback` |
| Web (local) | `http://localhost:8080/auth-callback`, `http://127.0.0.1:8080/auth-callback` |
| Web (production) | `https://basil.joetr.com/auth-callback` |

iOS uses the Firebase **iOS** OAuth client (`GoogleService-Info.plist` → `CLIENT_ID`), not the web client.

## Recovering secrets

GitHub secrets are write-only. Retrieve values from:

| Secret | Where to get it again |
|--------|----------------------|
| `FIREBASE_TOKEN` | `firebase login:ci` (revoke old tokens in Google account if needed) |
| `FIREBASE_WEB_API_KEY` | Firebase → Project settings → Your apps → Web |
| `FIREBASE_PROJECT_ID` | Firebase → Project settings → General |
| `BASIL_GOOGLE_WEB_CLIENT_ID` | Google Cloud → Credentials → OAuth Web client |
| `BASIL_GOOGLE_WEB_CLIENT_SECRET` | Same OAuth client (consider rotating — see below) |
| Android keystore | Local `.jks` file, or create a new one with `keytool -genkey` |

### Android keystore for CI

```sh
base64 -i path/to/basil-release.jks -o android-keystore-base64.txt
```

Paste into `ANDROID_KEYSTORE_BASE64`. Store password, alias, and key password in the matching secrets.

## Security notes

- Restrict the Firebase web API key in Google Cloud Console (HTTP referrers for web, app id for mobile).
- `BASIL_GOOGLE_WEB_CLIENT_SECRET` is compiled into desktop and wasm binaries today. Plan to move token exchange server-side or switch web/desktop to PKCE before broad public launch, then rotate the secret.
- `proxyImage` is a public HTTP endpoint with SSRF protections but no auth; monitor usage and add rate limits if abused.
