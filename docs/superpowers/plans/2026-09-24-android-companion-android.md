# Android Companion App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android APK that pairs with a nearby Windows VRCX and reads the active account's saved records without a VRChat login on the phone.

**Architecture:** Keep an independent Kotlin/Jetpack Compose app under `android/`. A small pinned-HTTPS client reads the versioned desktop API, a Keystore-backed store retains only pairing credentials, and ViewModels hold fetched records in memory. CameraX/ZXing reads the QR; the app never connects to VRChat itself.

**Tech Stack:** Android Gradle plugin 9.3.1, Gradle 9.5.0, JDK 21, Kotlin/Compose compiler 2.4.20, Compose BOM 2026.09.00, Android API 26–37, OkHttp 5.3.0, CameraX 1.6.2, ZXing 3.5.4, Kotlin serialization 1.11.0, JUnit/Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-24-android-companion-design.md`. Protocol contract: `docs/mobile-companion-api.md`, created by desktop Task 1. Matching server plan: `docs/superpowers/plans/2026-09-24-android-companion-desktop.md`.

## Global Constraints

- Same Wi-Fi only; Windows VRCX must be running with its opt-in service enabled. No cloud, VRChat login, write actions, offline history cache or background polling.
- Every data request must use the paired device token over TLS and verify the QR-pinned SPKI SHA-256 key. An IP change may update the address only when the pin remains identical.
- Only one selected desktop account is shown. Clear in-memory records on account change, device revocation or explicit unpairing.
- History timestamps represent VRCX observations; unknown exits, unobserved edits and legacy unowned game logs must not be displayed as known facts.
- App package ID `com.kyoko412.vrcxcompanion`, app label “VRCX Mirai Companion”; first APK supports Android 8.0+ (`minSdk=26`).
- `compileSdk=37` and `targetSdk=37`; request `ACCESS_LOCAL_NETWORK` at runtime on Android 17. Explain denial and offer retry. Camera permission is requested only when scanning.
- Use AGP 9 built-in Kotlin; do not apply `org.jetbrains.kotlin.android` alongside `com.android.application`. Java 21 is already present, but this computer has no Android SDK, ADB or Gradle project yet.

## Review Focus

1. QR with an external URL, public IP, bad port or malformed pin: reject it before a network request. Test in Task 2.
2. Same LAN IP serving a different TLS key: reject it even if a stored token exists; never offer a bypass. Test in Task 3.
3. Token revoked or account switched during a page load: clear the previous account's rows and show the correct state. Test in Tasks 4 and 6.
4. A 50-item page followed by another page with equal timestamps: preserve every row exactly once. Test in Task 4.
5. Android 17 local-network permission denied: no connection attempt, clear instruction and a retry path. Test in Tasks 2 and 6.

## File map and public seams

| Unit | Responsibility |
| --- | --- |
| `android/app/src/main/java/com/kyoko412/vrcxcompanion/pairing/` | QR validation, camera scanner, Keystore token storage |
| `android/app/src/main/java/com/kyoko412/vrcxcompanion/network/` | Pinned TLS, address mapping, API v1 parsing, errors |
| `android/app/src/main/java/com/kyoko412/vrcxcompanion/data/` | Repository methods matching the desktop contract |
| `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/` | Compose screens and ViewModels; records remain in memory |
| `android/app/src/test/` | Pure parser, client and ViewModel tests |
| `android/app/src/androidTest/` | Compose navigation, permission and screen behavior tests |

`PairingQr.parse(raw): PairingQr` validates the QR. `PinnedClientFactory.create(qrOrSavedPairing): OkHttpClient` maps `vrcx-companion.invalid` to the paired LAN IP and accepts only the pinned key; OkHttp's normal hostname verifier remains enabled. `CompanionApi` implements typed `status/friends/visits/encounters/bio/gameLog` methods. `PairingStore` saves `address`, `port`, `pin`, `deviceId`, and `token`, but no fetched history.

### Task 1: Independent Android project and contract models

**Files:** Create `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/app/build.gradle.kts`, `android/gradle.properties`, `android/gradle/wrapper/gradle-wrapper.properties`, wrapper scripts/JAR, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/MainActivity.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/network/ApiModels.kt`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/network/ApiModelsTest.kt`. Modify root `.gitignore` for `android/.gradle/`, `android/.kotlin/`, `android/local.properties`, `.apk`, `.aab`, `*.jks`, `*.keystore` without ignoring source files.

**Interfaces:** Kotlin `@Serializable` models match every JSON field in `docs/mobile-companion-api.md`. Unknown optional fields are ignored; unknown API major versions fail with `UnsupportedApiVersion`.

- [ ] **Step 1: Install Android SDK command-line/build tools and API 37 via official SDK Manager, then scaffold `android/` with the pinned Gradle wrapper, app module and manifest.** Install `platform-tools`, `platforms;android-37`, `build-tools;36.0.0`, `emulator` and an API 37 x86_64 system image; create AVD `vrcx-api37` for instrumentation tests and start it with `emulator -avd vrcx-api37` before connected tests. Configure AGP 9.3.1, Gradle 9.5.0, Kotlin/Compose and serialization plugins 2.4.20, Compose BOM 2026.09.00, `compileSdk=37`, `targetSdk=37`, `minSdk=26`, Activity Compose 1.13.0, lifecycle ViewModel Compose 2.11.0, OkHttp 5.3.0 and serialization JSON 1.11.0. Use JDK 21. Keep `local.properties` and downloaded SDK out of Git. Verify `java -version`, `android/gradlew.bat --version` and `adb devices`.
- [ ] **Step 2: Write a failing JVM test** parsing a real v1 friends page and rejecting a v2 status.

```kotlin
val page = json.decodeFromString<Page<FriendDto>>(
    """{"accountId":"usr_me","items":[{"id":"usr_a","displayName":"A"}],"nextCursor":null}"""
)
assertEquals("usr_a", page.items.single().id)
assertFailsWith<UnsupportedApiVersion> { parseStatus("""{"apiVersion":2}""") }
```

- [ ] **Step 3: Run `cd android; .\gradlew.bat testDebugUnitTest`; expect FAIL because models are absent.**
- [ ] **Step 4: Add the models and parser.** Set `buildFeatures.compose=true` in the already scaffolded app module. Do not apply `org.jetbrains.kotlin.android`; use AGP built-in Kotlin.

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android { namespace = "com.kyoko412.vrcxcompanion"; compileSdk = 37
    defaultConfig { applicationId = "com.kyoko412.vrcxcompanion"; minSdk = 26; targetSdk = 37 }
    buildFeatures { compose = true }
}
```

- [ ] **Step 5: Run `android/gradlew.bat testDebugUnitTest assembleDebug`; expect PASS and `android/app/build/outputs/apk/debug/app-debug.apk`.**
- [ ] **Step 6: Commit** `build: scaffold Android companion app and API models`.

Version evidence: [AGP 9.3/Gradle/API 37](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [Kotlin 2.4.20 compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose setup/BOM](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin).

### Task 2: QR validation, live scanner and runtime permissions

**Files:** Create `android/app/src/main/java/com/kyoko412/vrcxcompanion/pairing/PairingQr.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/pairing/QrScanner.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/PairingScreen.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/pairing/PermissionGate.kt`, `android/app/src/main/res/xml/network_security_config.xml`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/pairing/PairingQrTest.kt`, `android/app/src/androidTest/java/com/kyoko412/vrcxcompanion/pairing/PermissionGateTest.kt`. Modify `android/app/src/main/AndroidManifest.xml` for `INTERNET`, `CAMERA`, `ACCESS_LOCAL_NETWORK` and network security config.

**Interfaces:** QR JSON has `v=1`, private IPv4 `address`, port `1..65535`, fixed host `vrcx-companion.invalid`, `spkiSha256`, and one-time `secret`. `PermissionGate` exposes `CanConnect`, `NeedsPermission`, `Denied` states; no network request happens while denied.

- [ ] **Step 1: Write failing parser and permission tests.** Cover a valid `192.168.1.10`, bad JSON, missing pin, `https://evil.example`, IPv6/public address, port 0, oversized QR, camera denial, and Android 17 local-network denial.

```kotlin
assertFailsWith<InvalidPairingQr> {
    PairingQr.parse("""{"v":1,"address":"8.8.8.8","port":34682}""")
}
assertEquals(PermissionState.NeedsPermission,
    PermissionGate.state(apiLevel = 37, localNetworkGranted = false))
```

- [ ] **Step 2: Run `android/gradlew.bat testDebugUnitTest --tests "com.kyoko412.vrcxcompanion.pairing.PairingQrTest"` and `android/gradlew.bat connectedDebugAndroidTest`; expect FAIL.**
- [ ] **Step 3: Implement QR validation before networking and CameraX 1.6.2 + ZXing 3.5.4 live QR decoding.** Add `camera-core`, `camera-camera2`, `camera-lifecycle` and `camera-view` at 1.6.2 plus `com.google.zxing:core:3.5.4`. Use lifecycle-bound `ImageAnalysis`, close every `ImageProxy`, decode off the UI thread, stop camera after first accepted QR. Add a manual paste field as a fallback if a camera is unavailable. Request camera permission only on scan. Request `ACCESS_LOCAL_NETWORK` only where Android requires it and stop with a retry prompt on denial.
- [ ] **Step 4: Add a domain network security config with `cleartextTrafficPermitted="false"` and `<certificateTransparency enabled="false"/>` only for `vrcx-companion.invalid`; the server has a locally generated self-signed certificate.** Do not create a trust-all TLS setting.
- [ ] **Step 5: Run the two Step 2 test commands on AVD `vrcx-api37`; expect PASS.**
- [ ] **Step 6: Commit** `feat(android): scan and validate desktop pairing QR`.

Android 17 requires `ACCESS_LOCAL_NETWORK` for target API 37 [per Android documentation](https://developer.android.com/about/versions/17/behavior-changes-17). Its network security config supports a domain-scoped CT exception for a private certificate [here](https://developer.android.com/privacy-and-security/security-config).

### Task 3: Pinned HTTPS, pairing state and secure credential store

**Files:** Create `android/app/src/main/java/com/kyoko412/vrcxcompanion/network/PinnedClientFactory.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/network/CompanionApi.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/pairing/PairingStore.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/pairing/PairingViewModel.kt`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/network/PinnedClientTest.kt`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/pairing/PairingViewModelTest.kt`.

**Interfaces:** `PairingViewModel.request(qr, deviceName)` posts `/v1/pair/requests`, then polls `/v1/pair/redeem` with a short-lived poll secret until desktop approval/denial/expiry. `PairingStore` encrypts only the device token using Android Keystore AES-GCM and stores address, port, SPKI pin and device ID. `CompanionApi` adds bearer token to data requests, with bounded timeouts and no background retry loop.

- [ ] **Step 1: Write failing `MockWebServer` TLS tests** for correct pin, wrong pin, hostname mismatch, changed IP with unchanged pin, and expired pair offer; add a store test proving the token is absent from plain SharedPreferences and from logged errors.

```kotlin
val client = PinnedClientFactory.create(savedPairing.copy(spkiSha256 = wrongPin))
assertFailsWith<SSLException> {
    client.newCall(Request.Builder().url("https://vrcx-companion.invalid:34682/v1/status").build())
        .execute().use { it.body?.string() }
}
```

- [ ] **Step 2: Run `android/gradlew.bat testDebugUnitTest --tests "com.kyoko412.vrcxcompanion.network.PinnedClientTest" --tests "com.kyoko412.vrcxcompanion.pairing.PairingViewModelTest"`; expect FAIL.**
- [ ] **Step 3: Implement a trust manager that accepts only a valid-dated certificate with the saved QR SPKI SHA-256 key and retains OkHttp's default hostname verification.** Supply a custom OkHttp `Dns` mapping only `vrcx-companion.invalid` to the saved private IP; reject any other host/redirect, and prevent proxy use for the local client. Use the original QR pin for first pairing and the stored pin thereafter.
- [ ] **Step 4: Implement the pair request/redeem state machine and Keystore store; clear token on explicit unpair/revoke.** The app shows “waiting for computer approval” while pending and never persists fetched history.
- [ ] **Step 5: Run the Step 2 JVM test command; expect PASS.**
- [ ] **Step 6: Commit** `feat(android): pair through pinned HTTPS`.

### Task 4: Read repository, stable pagination and account state

**Files:** Create `android/app/src/main/java/com/kyoko412/vrcxcompanion/data/CompanionRepository.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/data/PageAccumulator.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/network/ApiFailure.kt`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/data/CompanionRepositoryTest.kt`.

**Interfaces:** `CompanionRepository` exposes `status()`, `friends(search,cursor)`, `worldVisits(id,cursor)`, `encounters(id,cursor)`, `bioHistory(id,cursor)` and `gameLog(cursor)`. A page is accepted only if its account ID equals the current status account; a new account clears every accumulated list. `PageAccumulator` deduplicates by stable item ID or event key, not timestamp alone.

- [ ] **Step 1: Add failing `MockWebServer` tests** for 50+1 pages, equal timestamps, non-ASCII names/bios, 400/401/403/409/503, a revoked token, and account ID changing between pages.

```kotlin
val first = repo.bioHistory("usr_friend", null)
val second = repo.bioHistory("usr_friend", first.nextCursor)
assertEquals(51, (first.items + second.items).map { it.id }.distinct().size)
```

- [ ] **Step 2: Run `android/gradlew.bat testDebugUnitTest --tests "com.kyoko412.vrcxcompanion.data.CompanionRepositoryTest"`; expect FAIL.**
- [ ] **Step 3: Implement exact contract JSON parsing and typed errors.** 401/403 clears pairing and data; 409 clears current pages and returns to connection state; 503 offers manual retry. Reject a response with a different account ID or API major version. Encode cursors as opaque query parameters without client-side decoding.
- [ ] **Step 4: Run the Step 2 repository test command; expect PASS.**
- [ ] **Step 5: Commit** `feat(android): read and page companion records`.

### Task 5: Friends and history Compose screens

**Files:** Create `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/HomeScreen.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/FriendsScreen.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/FriendDetailScreen.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/FriendsViewModel.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/HistoryViewModel.kt`, `android/app/src/androidTest/java/com/kyoko412/vrcxcompanion/ui/FriendScreensTest.kt`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/ui/HistoryViewModelTest.kt`.

**Interfaces:** Home shows computer/account/last successful read/connection state. Friends search matches cached names; detail has “地图访问”, “共同好友见面”, “简介历史” sections and loads 50-row pages on demand. Show `null` exit as “离开时间未观察到”; bio time as “本机观察到的变化时间”; zero records as “本机暂无记录”. Render text nodes, never HTML.

- [ ] **Step 1: Write failing ViewModel and Compose tests** for search, open by stable user ID after rename, three tabs, paging, empty state, multiline bio, uncertain exit and refresh.

```kotlin
composeRule.onNodeWithText("离开时间未观察到").assertExists()
composeRule.onNodeWithText("本机暂无记录").assertExists()
```

- [ ] **Step 2: Run `android/gradlew.bat testDebugUnitTest --tests "com.kyoko412.vrcxcompanion.ui.HistoryViewModelTest"` and `android/gradlew.bat connectedDebugAndroidTest`; expect FAIL.**
- [ ] **Step 3: Implement screen state with `StateFlow` and `LazyColumn`.** Cancel an old friend's request when selecting another; append only matching account/friend pages; preserve server timestamps and format locally. A manual refresh fetches from the server; do not start a periodic worker.
- [ ] **Step 4: Run the two Step 2 test commands and inspect narrow/large phone layouts and text scaling; expect PASS.**
- [ ] **Step 5: Commit** `feat(android): browse friend history on phone`.

### Task 6: My game log and connection failures

**Files:** Create `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/GameLogScreen.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/ConnectionState.kt`, `android/app/src/main/java/com/kyoko412/vrcxcompanion/ui/ConnectionViewModel.kt`, `android/app/src/test/java/com/kyoko412/vrcxcompanion/ui/ConnectionViewModelTest.kt`, `android/app/src/androidTest/java/com/kyoko412/vrcxcompanion/ui/ConnectionScreensTest.kt`. Modify app navigation in `android/app/src/main/java/com/kyoko412/vrcxcompanion/MainActivity.kt`.

**Interfaces:** Game log displays only the account-owned location sessions returned by `/v1/me/game-log`. Connection states distinguish computer offline, Wi-Fi mismatch, LAN permission denied, revoked device, account change, incompatible version and timeout. Nothing shows stale rows as current after a failed read.

- [ ] **Step 1: Add failing tests** for each failure type, offline after a previously successful page, 403 after revoked token, and a new account ID while the details screen is open.

```kotlin
viewModel.onFailure(ApiFailure.Revoked)
assertTrue(viewModel.state.value.records.isEmpty())
assertEquals(ConnectionState.Revoked, viewModel.state.value.connection)
```

- [ ] **Step 2: Run `android/gradlew.bat testDebugUnitTest --tests "com.kyoko412.vrcxcompanion.ui.ConnectionViewModelTest"` and `android/gradlew.bat connectedDebugAndroidTest`; expect FAIL.**
- [ ] **Step 3: Implement game-log page and recovery actions.** Offline/timeout offers retry, permission denied reopens permission flow, revoked device returns to pairing, account change clears all cached lists and asks for desktop reconfirmation. Show the message “仅显示已确认属于当前账号的记录；旧日志可能缺失” with the owned location list.
- [ ] **Step 4: Run the two Step 2 test commands; expect PASS.**
- [ ] **Step 5: Commit** `feat(android): show owned game logs and connection states`.

### Task 7: Build, physical LAN acceptance and APK delivery

**Files:** Create `.github/workflows/android-companion.yml`, `docs/mobile-companion-android-build.md`; update `docs/mobile-companion-testing.md` with actual outcomes. Add signing configuration to `android/app/build.gradle.kts` using environment variables; never commit a keystore/password.

**Interfaces:** CI runs `android/gradlew.bat testDebugUnitTest assembleDebug` on Windows (or `./gradlew` on Linux with JDK 21/SDK 37) and uploads the debug APK as an artifact. A signed release APK uses a persistent owner-controlled keystore supplied by GitHub Actions secrets; publish it only after device acceptance and the user's release authorization.

- [ ] **Step 1: Add a CI job with JDK 21, Android SDK 37, Gradle cache, JVM tests and APK build.** Confirm it runs on the branch and saves `app-debug.apk` as an artifact; a failing job is a build issue to fix before release.
- [ ] **Step 2: On a real Android phone and Windows PC on the same Wi-Fi, install the debug APK with `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`.** Verify camera scan, Android 17 LAN permission where applicable, desktop approval, friends, three histories and owned game logs.
- [ ] **Step 3: Verify negative paths** by switching the PC account, revoking the phone, disabling the service, changing the PC LAN IP, and trying a different server key at the same address. Record observed results and limitations in `docs/mobile-companion-testing.md`.
- [ ] **Step 4: Configure release signing with a persistent keystore stored outside Git, build `assembleRelease`, inspect package ID/signature with `apksigner verify --verbose`, and keep the APK as a reviewable artifact.** Upload it to a GitHub Release only when the user authorizes publication.
- [ ] **Step 5: Commit** `build(android): verify and package mobile companion`; push all commits to `origin/codex/android-companion`.

## Android plan completion gate

Run `android/gradlew.bat testDebugUnitTest assembleDebug`, required instrumentation tests, and a real same-Wi-Fi phone session against the desktop bridge. Verify no token, keystore, database copy or fetched history is committed or logged. Report the APK path and exact tests. If the Android SDK or physical phone is unavailable, state which acceptance checks remain unverified; do not call the APK production-ready.
