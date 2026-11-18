# PulseChat — Nutzer-Messaging & Owner-Konsole

Dual-Mode Android Messaging-App (Kotlin + Jetpack Compose + Material 3 + Firebase Firestore).

## Installation & Updates über GitHub + Obtainium

1. Repository auf GitHub pushen (auf `main`/`master` oder als Tag `v*`, z. B. `v1.0.0`).
   - Der GitHub-Actions-Workflow **Release APK (Obtainium)** (`.github/workflows/release.yml`) baut die APK automatisch mit Gradle `9.3.1`, lädt sie als Actions-Artifact hoch und erstellt ein GitHub Release (`PulseChat-v*.apk`).
   - Hinweis für GitHub-Einstellungen: Falls noch nicht aktiviert, unter **Settings → Actions → General → Workflow permissions** sicherstellen, dass **Read and write permissions** aktiviert ist.
2. In **Obtainium** auf dem Android-Gerät:
   - **App hinzufügen** → GitHub-Repository-URL einfügen (`https://github.com/<DEIN_NAME>/<DEIN_REPO>`), **oder**
   - In PulseChat unter **GitHub & Obtainium** dein Repository (`owner/repo`) eintragen und direkt auf **„In Obtainium“** oder **„JSON kopieren“** tippen.
3. Obtainium erkennt neue GitHub Releases automatisch anhand der angehängten `.apk`-Datei (`PulseChat-v*.apk`).
