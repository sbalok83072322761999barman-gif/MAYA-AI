# Maya AI — Android CI/CD & GitHub Releases

Automated Android Debug and Signed Release APK/AAB build pipelines for **Maya AI** (`com.aistudio.mayaai.kx7vqm`) using GitHub Actions.

## Project Build Profile

- **Application ID**: `com.aistudio.mayaai.kx7vqm`
- **Namespace**: `com.example`
- **Min SDK / Target SDK / Compile SDK**: `24` / `36` / `36`
- **Gradle / AGP / Kotlin / JDK**: `9.3.1` / `9.1.1` / `2.4.21` / `JDK 21 (Temurin)`
- **Default Version**: `versionCode = 1`, `versionName = "1.0"` (overridden automatically during CI releases via `-PVERSION_NAME` / `-PVERSION_CODE`)

---

## Workflows

| Workflow | File | Triggers | Outputs |
|---|---|---|---|
| **Android Debug CI** | `.github/workflows/android-debug.yml` | `push`, `pull_request`, `workflow_dispatch` | Runs unit tests + `./gradlew clean assembleDebug --no-daemon`; uploads `Maya-Debug-APK` artifact |
| **Android Release & GitHub Release** | `.github/workflows/android-release.yml` | `push` to `main`, `push` tags `v*`, `workflow_dispatch` | Runs tests, builds signed `Maya-v<ver>-release.apk`, `Maya-v<ver>-release.aab`, `Maya-v<ver>-debug.apk`, `.sha256` checksums, and publishes GitHub Release |

> **Security Behavior when Signing Secrets Are Missing**:
> - Unsigned APKs are **never** published as signed releases.
> - On `main` / manual runs without secrets, the workflow builds and uploads `Maya-Debug-APK` only and reports the missing secrets.
> - On `v*` tag pushes without secrets, the workflow fails immediately with a clear error listing the missing secrets.

---

## 1. Create a Release Keystore & Encode as Base64

If you do not already have an Android upload/release keystore, generate one locally (**keep this file safe and never commit it to Git**):

```bash
keytool -genkeypair -v \
  -keystore maya-release-key.jks \
  -storetype JKS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias upload
```

Encode your keystore to single-line Base64:

- **Linux / macOS**:
  ```bash
  base64 -w 0 maya-release-key.jks > keystore.b64   # Linux
  base64 -i maya-release-key.jks | tr -d '\n' > keystore.b64   # macOS
  ```
- **Windows (PowerShell)**:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("maya-release-key.jks")) | Out-File -NoNewline keystore.b64
  ```

Delete `keystore.b64` after pasting its contents into GitHub Secrets, and store `maya-release-key.jks` securely offline (losing it prevents future updates to the same signed app).

---

## 2. Configure GitHub Repository Secrets

Go to your GitHub repository → **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret Name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Contents of `keystore.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore (e.g. `upload`) |
| `ANDROID_KEY_PASSWORD` | Key password for the alias |

---

## 3. Trigger & Download Debug APK

- **Automatic**: Push to any branch or open a pull request.
- **Manual**: GitHub → **Actions → Android Debug CI → Run workflow**.
- **Download**: Open the completed workflow run → **Artifacts → `Maya-Debug-APK`**.

---

## 4. Create a Version Release & Download Signed Release APK

Push a semantic version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Or run **Actions → Android Release & GitHub Release Automation → Run workflow** (or push to `main`).

Once complete, download the signed APK (`Maya-v1.0.0-release.apk`), Debug APK, AAB bundle, and `.sha256` checksums from:

**GitHub Repository → Releases → Latest Release → Assets**
