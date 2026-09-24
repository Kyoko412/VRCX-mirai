# Android Companion Desktop Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a paired Android phone read the currently logged-in Windows CEF VRCX account's saved records over the same Wi-Fi.

**Architecture:** Add a Windows-only `VRCX.MobileCompanion` class library with an authenticated Kestrel HTTPS listener, a session-bound read model, and pairing state. The CEF host supplies the current account, verified friend IDs and the existing SQLite connection through a narrow adapter; the Vue settings page controls the opt-in service.

**Tech Stack:** .NET 10, ASP.NET Core Kestrel, `System.Data.SQLite`, xUnit, Vue 3/Vitest, Kotlin client contract in `docs/mobile-companion-api.md`.

**Spec:** `docs/superpowers/specs/2026-09-24-android-companion-design.md`. The matching Android client plan is `docs/superpowers/plans/2026-09-24-android-companion-android.md`.

## Global Constraints

- First release supports the Windows CEF application (`Dotnet/VRCX-Cef.csproj`, x64); keep Electron/Linux/macOS compiling but do not expose this server there.
- The service starts only after the user enables it in VRCX settings and is bound to a selected private-network IPv4 address. No cloud, public endpoint, generic SQL or raw database file access.
- Use an active account ID beginning with `usr_` and a verified friend-ID allowlist. Logout/switch clears the native session before returning data, including in-flight responses.
- Device traffic uses TLS with a stable certificate and public-key pin; all data routes require a bound device token. Pairing requires a short-lived QR secret and explicit desktop approval.
- Existing Overlay WebSocket remains on `127.0.0.1:34582`; do not reuse or widen it.
- Expose only cached desktop records. A missing entry means “not observed here,” and all times are observations rather than guaranteed VRChat event times.
- Global legacy `gamelog_*` rows lack owner IDs. Expose only newly tagged location-session rows that belong to the active account; never infer ownership of old rows.
- Current machine has no .NET SDK (`dotnet --list-sdks` is empty). Install .NET 10 SDK before executing build/test steps. The Android SDK is handled by the companion Android plan.

## Review Focus

1. Logout during an in-flight read: the HTTP response must fail rather than return either account's records. Test in Task 2 and Task 7.
2. A friend ID removed from the current friend allowlist: a direct history URL must return 404 after the update. Test in Task 3 and Task 7.
3. Duplicate timestamps in bio/encounter/visit data: pagination must neither skip nor duplicate rows. Test in Tasks 3 and 4.
4. Replayed old game logs after login: replay must never acquire the logged-in account's ownership marker. Test in Task 5.
5. Expired, reused, rejected or revoked pairing credentials: no account data or reusable token must leak. Test in Tasks 6 and 7.

## File map and public seams

| Unit                                                        | Responsibility                                                            |
| ----------------------------------------------------------- | ------------------------------------------------------------------------- |
| `Dotnet/MobileCompanion/Contract/`                          | API v1 DTOs, validation limits, error types; no UI or database globals    |
| `Dotnet/MobileCompanion/Session/`                           | Immutable account snapshot and generation checks                          |
| `Dotnet/MobileCompanion/Data/`                              | Parameterized, allowlisted read queries and visit reconstruction          |
| `Dotnet/MobileCompanion/Security/`                          | Certificate persistence, pairing, token hashes and revocation             |
| `Dotnet/MobileCompanion/Server/`                            | Private-interface Kestrel listener and versioned HTTP routes              |
| `Dotnet/AppApi/Cef/MobileCompanionBridge.cs`                | CEF-only adapter for `SQLite.Instance`, account events and settings calls |
| `src/views/Settings/components/MobileCompanionSettings.vue` | Switch, address, QR, approval and device management                       |
| `android/`                                                  | Independent client, implemented by the matching Android plan              |

`ICompanionDb.Query(string sql, IDictionary<string, object> args)` is the only database capability given to the library. `MobileSession.Open(accountId, friendIds)`, `UpdateFriends(friendIds)`, `Close()`, `Capture()` and `IsCurrent(generation)` define the account gate. `CompanionRepository` takes `ICompanionDb` and a captured session snapshot; route handlers never accept a table prefix or raw SQL from a request.

### Task 1: API contract and compilable testable library

**Files:** Create `docs/mobile-companion-api.md`, `Dotnet/MobileCompanion/VRCX.MobileCompanion.csproj`, `Dotnet/MobileCompanion/Contract/ApiDtos.cs`, `Dotnet/MobileCompanion/Data/ICompanionDb.cs`, `tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj`, `tests/MobileCompanion.Tests/ContractTests.cs`. Modify `Dotnet/VRCX-Cef.csproj`, `Dotnet/VRCX-Electron.csproj`, `.gitignore` only as needed for new build output.

**Interfaces:** Define `Page<T>(string AccountId, IReadOnlyList<T> Items, string? NextCursor)`, `ApiError(string Code, string Message)` and the typed DTOs `FriendDto`, `VisitDto`, `EncounterDto`, `BioChangeDto`, `GameLocationDto`. Item identity is `FriendDto.Id`, `VisitDto.EventKey`, `EncounterDto.VisitKey`, `BioChangeDto.Id`, or `GameLocationDto.Id`; this survives equal timestamps. Every authenticated data response includes `accountId`; pairing responses do not. Wire CEF as a project reference; exclude `MobileCompanion/**` from both existing projects' wildcard compilation and from Electron's reference graph.

- [ ] **Step 1: Scaffold the empty class library and xUnit test project.** Run `dotnet new classlib -f net10.0 -o Dotnet/MobileCompanion` and `dotnet new xunit -f net10.0 -o tests/MobileCompanion.Tests`; rename the generated project files to the names in this task, set both target frameworks to `net10.0-windows`, and add a project reference from tests to the library. Remove generated `Class1.cs`/`UnitTest1.cs`.
- [ ] **Step 2: Write the contract and a failing serialization test.** Document `/v1/status`, `/v1/friends`, three friend histories, `/v1/me/game-log`, `POST /v1/pair/requests` and `POST /v1/pair/redeem` with exact request/response JSON, UTC ISO-8601 timestamps, cursor, error codes and API version 1. Pair request returns `requestId,pollSecret`; redeem returns HTTP 202 while pending or HTTP 200 with `deviceId,token` after desktop approval. Define 400/401/403/404/409/503 and include a test such as:

```csharp
var page = new Page<FriendDto>("usr_me", [new("usr_a", "A")], null);
Assert.Equal("usr_a", JsonSerializer.Deserialize<Page<FriendDto>>(
    JsonSerializer.Serialize(page))!.Items[0].Id);
```

```json
{ "accountId": "usr_me", "items": [{ "id": "usr_a", "displayName": "A" }], "nextCursor": null }
```

- [ ] **Step 3: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release`; expect FAIL because DTOs are absent.**
- [ ] **Step 4: Create the DTO records with `System.Text.Json` camel-case options; add `<FrameworkReference Include="Microsoft.AspNetCore.App" />` to the library and `<ProjectReference Include="MobileCompanion/VRCX.MobileCompanion.csproj" />` to CEF.** Keep the test project outside `Dotnet/` so default source inclusion cannot compile test files into VRCX.

```csharp
public sealed record FriendDto(string Id, string DisplayName);
public sealed record Page<T>(string AccountId, IReadOnlyList<T> Items, string? NextCursor);
public interface ICompanionDb {
    object[][] Query(string sql, IDictionary<string, object> args);
}
```

- [ ] **Step 5: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~ContractTests` and `dotnet build Dotnet/VRCX-Cef.csproj -c Release -p:Platform=x64 --runtime win-x64`; expect PASS.** Build `Dotnet/VRCX-Electron.csproj` on CI to catch accidental Windows-only compilation.
- [ ] **Step 6: Commit** `feat: define mobile companion API contract and library`.

### Task 2: Native account session and friend allowlist

**Files:** Create `Dotnet/MobileCompanion/Session/MobileSession.cs`, `tests/MobileCompanion.Tests/MobileSessionTests.cs`.

**Interfaces:** `MobileSession.Open(string accountId, IReadOnlyCollection<string> friendIds)`, `UpdateFriends(IReadOnlyCollection<string> friendIds)`, `Close()`, `Capture(): SessionSnapshot?`, `IsCurrent(long generation): bool`; snapshot contains `AccountId`, `TablePrefix`, `FriendIds`, `Generation`. Derive the SQL table prefix only from a validated account ID, using the same dash/underscore stripping as `src/services/database/index.js:57-63`.

- [ ] **Step 1: Add failing tests** for an invalid account ID, friend-ID membership, logout, account switch and a captured generation becoming stale after `Close()` or `Open()`.

```csharp
session.Open("usr_a", ["usr_friend"]);
var before = session.Capture()!;
session.Close();
Assert.False(session.IsCurrent(before.Generation));
Assert.Null(session.Capture());
```

- [ ] **Step 2: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~MobileSessionTests`; expect FAIL.**
- [ ] **Step 3: Implement the immutable snapshot under one lock.** Reject IDs outside `^usr_[A-Za-z0-9_-]{1,64}$`; increment the generation on every open, friend update and close; copy the friend set rather than retaining the caller's mutable collection. A request checks the captured generation again just before sending its result.
- [ ] **Step 4: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~MobileSessionTests`; expect PASS.**
- [ ] **Step 5: Commit** `feat: bind companion reads to active account sessions`.

### Task 3: Friend list, bio history and mutual encounters

**Files:** Create `Dotnet/MobileCompanion/Data/CompanionRepository.cs`, `Dotnet/MobileCompanion/Data/KeysetCursor.cs`, `tests/MobileCompanion.Tests/CompanionRepositoryTests.cs`.

**Interfaces:** `ListFriends(snapshot, search, cursor, limit)`, `GetBioHistory(snapshot, friendId, cursor, limit)` return typed pages; `GetEncounters(snapshot, friendId, cursor, limit)` returns `EncounterPage(AccountId, QualifiedCount, UnknownCount, Items, NextCursor)`. `ICompanionDb` is injected, not a global. Validate `limit` in `1..50`, search length `<=100`, cursor format and friend membership before querying.

- [ ] **Step 1: Write failing tests** using a fake `ICompanionDb` that records SQL and parameters. Cover allowlist intersection with `${prefix}_friend_log_current`, another account's table never appearing, a removed friend returning 404, names containing `'`, empty history, and `(created_at,id)` or `(observed_at,visit_key)` ties.

```csharp
var snapshot = session.Capture()!;
Assert.Throws<FriendNotFoundException>(() =>
    repository.GetBioHistory(snapshot, "usr_not_friend", null, 20));
Assert.DoesNotContain("usr_not_friend", fakeDb.ExecutedSql);
```

- [ ] **Step 2: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~CompanionRepositoryTests`; expect FAIL.**
- [ ] **Step 3: Implement SQL with parameters for all values.** Use only `snapshot.TablePrefix` for identifiers. Query friends from `${prefix}_friend_log_current` and intersect with the current snapshot allowlist; mark the list as cached. Bio rows come from `${prefix}_feed_bio` (non-identical old/new text only), encounters from `${prefix}_mutual_encounters_v1` with `status='qualified'`; unknown encounters affect only the summary count.

```sql
SELECT created_at, id, bio, previous_bio
FROM {trustedPrefix}_feed_bio
WHERE user_id = @friendId AND (created_at < @at OR (created_at = @at AND id < @id))
ORDER BY created_at DESC, id DESC LIMIT @take;
```

- [ ] **Step 4: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~CompanionRepositoryTests`; expect PASS, including the 51-row two-page fixture.**
- [ ] **Step 5: Commit** `feat: expose account-scoped friend and history reads`.

### Task 4: Reconstruct and page friend world visits

**Files:** Create `Dotnet/MobileCompanion/Data/FriendVisitBuilder.cs`, `tests/MobileCompanion.Tests/FriendVisitBuilderTests.cs`; extend `CompanionRepository.cs` and its tests.

**Interfaces:** `GetWorldVisits(snapshot, friendId, cursor, limit): Page<VisitDto>`. `VisitDto` has stable `EventKey`, `WorldId`, `WorldName`, `Location`, nullable `EnteredAt`, `ExitedAt`, `DurationMs`, `ObservedAt`, and per-world `VisitCount`. Port the semantics of `src/views/FriendWorldVisits/buildFriendWorldVisits.js`, not its Vue rendering.

- [ ] **Step 1: Copy the existing JS fixture cases into failing C# tests** for duplicate GPS rows, offline close, a first GPS event with only previous-world evidence, uncertain exit `null`, a reported duration within the 30-minute tolerance, and equal timestamps.

```csharp
var visits = FriendVisitBuilder.Build(events);
Assert.Null(visits[0].ExitedAt); // no later observation proves an exit
Assert.Equal(2, visits.Count(v => v.WorldId == "wrld_a"));
```

- [ ] **Step 2: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~FriendVisitBuilderTests`; expect FAIL.**
- [ ] **Step 3: Implement the ordered GPS + Online/Offline read and builder.** Preserve the JS event ordering (`created_at`, Online/GPS/Offline order, `id`), the previous-location fallback and nullable uncertain values. Sort flattened visits by observation time plus deterministic event key, compute full per-world counts before applying the 50-item page, and use a stable cursor; never accept a client-supplied SQL date filter.
- [ ] **Step 4: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter "FullyQualifiedName~FriendVisitBuilderTests|FullyQualifiedName~CompanionRepositoryTests"`; expect PASS, including the 101-visit, same-timestamp two-page fixture.**
- [ ] **Step 5: Commit** `feat: serve observed friend world visits`.

### Task 5: Account-owned new game location sessions

**Files:** Modify `src/services/database/index.js`, `src/services/database/gameLog.js`, `src/coordinators/gameLogCoordinator.js`; create `src/services/database/__tests__/mobileGameOwnership.test.js`, `Dotnet/MobileCompanion/Data/GameLocationRepository.cs`, `tests/MobileCompanion.Tests/GameLocationRepositoryTests.cs`.

**Interfaces:** Add global `mobile_game_location_owner_v1` with `gamelog_location_id INTEGER PRIMARY KEY` and `owner_user_id TEXT NOT NULL`. The primary key prevents two accounts from claiming one global row. New live location writes attach the current logged-in account; replay (`trackEncounter:false`) and logged-out writes never attach ownership. `GetGameLog(snapshot,cursor,limit)` reads only exact-owner joins and returns location sessions, not all raw log event categories.

- [ ] **Step 1: Add failing JS and C# tests.** JS: a live `location` event creates a marker, a replayed event does not, and two accounts cannot claim each other's location. C#: old global `gamelog_location` rows without a matching owner row return no mobile items.

```js
addGameLogEntry(locationEvent, '', { trackEncounter: false });
expect(database.markMobileGameLocationOwner).not.toHaveBeenCalled();
```

- [ ] **Step 2: Run `npm test -- src/services/database/__tests__/mobileGameOwnership.test.js` and `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~GameLocationRepositoryTests`; expect FAIL.**
- [ ] **Step 3: Add the owner table and write path.** After a successful live `gamelog_location` insert, find its ID by unique `(created_at, location)` and insert its owner once into `mobile_game_location_owner_v1`; do this only when `watchState.isLoggedIn` and the current account ID is present, never during historical replay. Do not change the global desktop game log behavior.
- [ ] **Step 4: Implement parameterized exact-owner join and keyset page `(created_at,id)`; run the two commands from Step 2; expect PASS.**
- [ ] **Step 5: Commit** `feat: tag new game locations by account for mobile`.

### Task 6: TLS identity, pairing approval and device revocation

**Files:** Create `Dotnet/MobileCompanion/Security/CertificateStore.cs`, `PairingCoordinator.cs`, `DeviceRegistry.cs`, `tests/MobileCompanion.Tests/PairingTests.cs`, `CertificateStoreTests.cs`.

**Interfaces:** `CertificateStore.GetOrCreate()` returns a stable self-signed cert with DNS SAN `vrcx-companion.invalid` and SPKI SHA-256 pin. `PairingCoordinator.CreateOffer(address,port)` returns two-minute, one-use QR JSON with `v,address,port,host,spkiSha256,secret`; `RequestPair(secret,name)`, `Approve(requestId,accountId)`, `Reject(requestId)`, `Redeem(requestId,pollSecret)`, `Revoke(deviceId)` manage pairing. `DeviceRegistry.Authorize(token,accountId)` uses fixed-time hash comparison. Persist only token hashes/device metadata through `IDeviceStateStore.Load/Save` (CEF adapter backed by `VRCXStorage`); keep the cert private key in the Windows CurrentUser certificate store.

- [ ] **Step 1: Write failing tests** for expiry, replay, approval requirement, poll-secret mismatch, token bound to account, revoke, 256-bit token uniqueness, persisted certificate pin after restart, and rate limits for bad requests.

```csharp
var offer = pairing.CreateOffer("192.168.1.10", 34682);
clock.Advance(TimeSpan.FromMinutes(3));
Assert.Throws<ExpiredOfferException>(() => pairing.RequestPair(offer.Secret, "Phone"));
```

- [ ] **Step 2: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter "FullyQualifiedName~PairingTests|FullyQualifiedName~CertificateStoreTests"`; expect FAIL.**
- [ ] **Step 3: Implement the state machine and persistence.** Generate secrets with `RandomNumberGenerator.GetBytes(32)`, store SHA-256 token hashes, compare with `CryptographicOperations.FixedTimeEquals`, cap pending requests and attempts per source address, sanitize displayed device names and never log secrets. Certificate generation includes the fixed DNS SAN, so an Android client can route that name to a changing LAN IP while still verifying host and pin.
- [ ] **Step 4: Run the Step 2 security test command; expect PASS.**
- [ ] **Step 5: Commit** `feat: add approved TLS pairing for mobile devices`.

### Task 7: Authenticated private-network HTTP server

**Files:** Create `Dotnet/MobileCompanion/Server/CompanionHost.cs`, `CompanionRoutes.cs`, `PrivateAddressSelector.cs`, `tests/MobileCompanion.Tests/HttpServerTests.cs`.

**Interfaces:** `CompanionHost.StartAsync(IPAddress selectedAddress, int port, CancellationToken)` binds Kestrel to exactly that selected address with the certificate from Task 6; `StopAsync()` shuts down the listener. Only `/v1/pair/*` accepts an unauthenticated request, and those routes return no account data. All `/v1/*` data routes capture and recheck the `MobileSession` generation around the repository call.

- [ ] **Step 1: Add loopback integration tests** for TLS, 401 without token, 403 revoked token, wrong account token, 404 non-friend ID, bad cursor/limit 400, database exception 503, and logout while a fake DB read is blocked.

```csharp
var pending = client.GetAsync("/v1/friends");
fakeDb.WaitUntilReadStarted();
session.Close();
fakeDb.ReleaseRead();
Assert.Equal(HttpStatusCode.Conflict, (await pending).StatusCode);
```

- [ ] **Step 2: Run `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release --filter FullyQualifiedName~HttpServerTests`; expect FAIL.**
- [ ] **Step 3: Implement routes and centralized error mapping.** Use `Authorization: Bearer <token>` on data routes, constant JSON error shape, server-side `limit<=50`, maximum body size for pairing, no wildcard CORS, no database details in errors. Recheck the captured session generation immediately before serialization/response. Check Windows network profile is Private before offering an interface, and refuse loopback/public/WAN addresses in the production selector; tests can inject loopback.
- [ ] **Step 4: Run the Step 2 host test command; expect PASS. Verify `http://127.0.0.1:34582/` is unchanged in the Overlay source and the new server cannot bind unless explicitly started.**
- [ ] **Step 5: Commit** `feat: serve authorized companion reads on private LAN`.

### Task 8: CEF lifecycle and desktop settings

**Files:** Create `Dotnet/AppApi/Cef/MobileCompanionBridge.cs`, `src/views/Settings/components/MobileCompanionSettings.vue`, `src/views/Settings/components/__tests__/MobileCompanionSettings.test.js`. Modify `Dotnet/Program.cs`, `Dotnet/AppApi/Cef/AppApiCef.cs`, `src/coordinators/authCoordinator.js`, `src/stores/auth.js`, the friend-sync completion path in `src/stores/friend.js`, `src/views/Settings/components/Tabs/IntegrationsTab.vue`, and localization strings.

**Interfaces:** The CEF `AppApi` binding exposes `MobileCompanionGetState`, `Enable`, `Disable`, `CreateOffer`, `ListPending`, `Approve`, `Reject`, `ListDevices`, `Revoke`, `SetActiveAccount`, `SetVerifiedFriends`, `ClearActiveAccount`. The shared Vue page hides this section when the CEF-only bridge is unavailable. The service remains running on close-to-tray and stops before SQLite on real exit.

- [ ] **Step 1: Add failing Vitest tests** for default-off switch, QR hidden until enabled, pending device approval/rejection, revoke, errors and unavailable bridge; add a native session transition test for logout-before-clear.

```js
await wrapper.find('[data-testid="mobile-companion-enable"]').trigger('click');
expect(AppApi.MobileCompanionEnable).toHaveBeenCalledTimes(1);
expect(wrapper.find('[data-testid="pairing-qr"]').exists()).toBe(false);
```

- [ ] **Step 2: Run `npm test -- src/views/Settings/components/__tests__/MobileCompanionSettings.test.js`; expect FAIL.**
- [ ] **Step 3: Add the CEF bridge and lifecycle.** Await native `ClearActiveAccount` as the first operation in `runLogoutFlow`, before changing local login state or other side effects; set account only after `database.initUserTables` and login success, update friend allowlist after the verified friend sync. Initialize the manager after SQLite in `Program.Run`, stop it before `SQLite.Instance.Exit`. Reopening from tray does not stop it. Keep opt-in off at each program launch until the user enables it.
- [ ] **Step 4: Add the settings UI** using existing `SettingsGroup`, `SettingsItem`, `Switch`; install `qrcode@1.5.4` with `npm install qrcode@1.5.4`, render the QR, and display selected address, pending request, paired devices, revoke, connection and Windows Private firewall guidance. All user-facing text is localized. In shared Electron UI, check CEF bridge availability before rendering.
- [ ] **Step 5: Run `npm test -- src/views/Settings/components/__tests__/MobileCompanionSettings.test.js`, `npm run lint`, `npm run format:check`, `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release` and `dotnet build Dotnet/VRCX-Cef.csproj -c Release -p:Platform=x64 --runtime win-x64`; expect PASS for changed code.**
- [ ] **Step 6: Commit** `feat: control mobile companion from VRCX settings`.

### Task 9: Cross-process verification and CI

**Files:** Modify `.github/workflows/ci.yaml`; create `docs/mobile-companion-testing.md`; update `docs/mobile-companion-api.md` only for verified contract corrections.

**Interfaces:** A Windows CI job runs the library tests and CEF build; a second LAN device can act as a protocol client for the desktop smoke test. Android UI and APK publishing are covered in the Android plan.

- [ ] **Step 1: Add a failing contract/integration fixture** that requests each data route against a two-account SQLite fixture and confirms a foreign account's marker and legacy global game row never appear.
- [ ] **Step 2: Run tests and confirm that fixture fails before the final adapter/query correction, then make the minimal correction.**
- [ ] **Step 3: Add CI commands:** `dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release`, existing CEF build, and focused `npm test` for account ownership/settings. Document .NET 10 SDK prerequisite and the exact `build/Cef/VRCX.exe` location.
- [ ] **Step 4: From a second LAN device, use a test HTTPS client with the QR pin to verify pairing approval, friend data, account switch, token revocation, IP change and Private-only firewall allow. Record results in `docs/mobile-companion-testing.md`; the Android phone run is the Android plan's final gate.**
- [ ] **Step 5: Commit** `test: verify desktop mobile companion and CI`.

## Desktop plan completion gate

Run the focused JS/.NET suites, `npm run lint`, `npm run format:check`, Windows CEF build, and a private-LAN protocol smoke test. Verify the worktree is clean and every task commit is pushed to `origin/codex/android-companion`. Do not claim physical-phone acceptance until the Android plan's device run succeeds.
