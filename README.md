# Basil

A Compose Multiplatform recipe library for Android, iOS, desktop, and web.

Basil icon by [Freepik](https://www.flaticon.com/free-icon/basil_8154210) from Flaticon.

## Setup

1. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
2. Add Firebase web config (safe to ship — Security Rules protect data):

```properties
basil.firebase.apiKey=your-web-api-key
basil.firebase.projectId=your-project-id
basil.firebase.authDomain=your-project-id.firebaseapp.com
basil.firebase.storageBucket=your-project-id.appspot.com
basil.firebase.functionsRegion=us-central1
basil.google.webClientId=your-google-oauth-web-client-id.apps.googleusercontent.com
basil.google.webClientSecret=your-google-oauth-web-client-secret
```

Or set `FIREBASE_WEB_API_KEY`, `FIREBASE_PROJECT_ID`, and `BASIL_GOOGLE_WEB_CLIENT_ID` environment variables.

3. Place `androidApp/google-services.json` and `iosApp/iosApp/GoogleService-Info.plist` (gitignored; inject from CI secrets).
4. Deploy the Firebase backend (Cloud Functions, Firestore rules/indexes, Storage rules):

```bash
firebase login
./scripts/deploy-firebase-backend.sh --smoke-test
```

Every push to `main` runs the same deploy via GitHub Actions. See [docs/github-actions.md](docs/github-actions.md) for CI secrets, DNS, GitHub Pages, and how to recover credentials.

Enable Anonymous, Email/Password, and Google auth providers in the Firebase console. Add `basil.joetr.com` to authorized domains.

### Google OAuth redirect URIs

In [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials → your **Web client** (same ID as `basil.google.webClientId`), add these **Authorized redirect URIs**:

| Platform | Redirect URI |
|----------|--------------|
| Desktop | `http://127.0.0.1:3847/auth-callback` |
| Web (local dev) | `{origin}/auth-callback` — e.g. `http://localhost:8080/auth-callback` (use the exact origin from your browser address bar; add both `localhost` and `127.0.0.1` if you use both) |
| Web (production) | `https://basil.joetr.com/auth-callback` |
| iOS | Custom URI scheme auto-configured from the Firebase **iOS** OAuth client (see below) |

Desktop and web use the authorization-code flow and require `basil.google.webClientSecret` in `local.properties` (or `BASIL_GOOGLE_WEB_CLIENT_SECRET` in the environment).

**iOS Google sign-in** uses the Firebase **iOS** OAuth client (not the Web client). Add an iOS app in Firebase with bundle ID `com.joetr.basil`, download `GoogleService-Info.plist` into `iosApp/iosApp/`, or set `basil.google.iosClientId` in `local.properties` to the plist's `CLIENT_ID` value. Rebuild so the reversed client ID URL scheme is registered in `Info.plist`.

## Run

- Desktop: `./gradlew :composeApp:run`
- Android: `./gradlew :androidApp:installDebug`
- Web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- iOS: Open `iosApp/iosApp.xcodeproj` in Xcode, select the **iosApp** scheme, choose a simulator or device, then press Run (⌘R).

## Architecture

Layered KMP modules with Metro DI, SQLDelight offline storage, and Firebase sync/auth.

- **Android/iOS**: Firebase Auth / Firestore / Storage / Functions via REST (Identity Toolkit, Firestore REST, Storage JSON API) with native Google Sign-In on Android (Credential Manager) and OAuth redirect on iOS.
- **Desktop/web**: same Firebase REST APIs via Ktor (no official Firebase KMP SDK for JVM/wasmJs).

```
feature/* → domain + ui + navigation
data/*    → domain + core:*
core/*    → platform utilities, Firebase REST network, database
```

## License

MIT
