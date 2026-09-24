# Mobile companion test record

The desktop companion requires **.NET 10 SDK** to build. After `npm ci`, build the
Windows CEF app with:

```powershell
dotnet test tests/MobileCompanion.Tests/VRCX.MobileCompanion.Tests.csproj -c Release
npm test -- src/services/database/__tests__/mobileGameOwnership.test.js src/coordinators/__tests__/mobileCompanionLogout.test.js src/views/Settings/components/__tests__/MobileCompanionSettings.test.js
dotnet build Dotnet/VRCX-Cef.csproj -c Release -p:Platform=x64 --runtime win-x64
```

The resulting development executable is `build/Cef/VRCX.exe`; the installed
`E:\wn\VRCX\VRCX.exe` does not include these source changes. The service starts
disabled on every launch. In Settings → Integrations, select a Windows **Private**
LAN address, enable access, and create a two-minute QR. The PC firewall must
allow inbound TCP port **34682** on the Private profile only. The phone and PC
must be on the same Wi-Fi, without client isolation.

## Automated results (2026-09-24)

| Check                                                                         | Result                                                                    |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| .NET library and HTTPS route tests, including real two-account SQLite fixture | 40 passed                                                                 |
| Frontend companion settings, logout, game ownership                           | 8 passed                                                                  |
| CEF x64 Release build                                                         | Passed after settings integration                                         |
| Full format check                                                             | Passed after formatter alignment                                          |
| Android JVM tests                                                             | 27 passed                                                                 |
| Android debug APK and instrumentation APK compilation                         | Passed                                                                    |
| Android CI debug APK artifact                                                 | [Passed](https://github.com/Kyoko412/VRCX-mirai/actions/runs/35976995378) |
| Release signing with a disposable test key                                    | Built and verified (v2); test key and APK deleted                         |
| Android instrumentation execution                                             | Blocked: no online device                                                 |
| Full frontend `npm test` suite                                                | Existing unrelated cases fail; see note below                             |

The two-account fixture requests all five data routes over a live loopback HTTPS
server, switches from `usr_a` to `usr_b`, and checks that the old token is
rejected. It verifies account-specific friend, visit, encounter, and bio tables
and the owner-marked game log. A legacy game row without an owner is hidden.
Loopback is injected **only in the test**; the production selector rejects it.

The full frontend suite does not pass on this checkout. For example, unchanged
`src/shared/utils/__tests__/user.test.js` calls `userOnlineFor`, which the
unchanged `src/shared/utils/user.js` does not export. The three frontend test
files for this companion feature pass all 8 tests. The full-suite failure is
recorded here rather than counted as a companion regression.

## Physical LAN acceptance

These checks require a second LAN device and the Android APK. Record the date,
Windows address, Android version, and outcome when run:

| Check                                                                     | Status      |
| ------------------------------------------------------------------------- | ----------- |
| Scan QR, request pairing, approve on PC, read friends and three histories | Not run yet |
| Read account-owned game log                                               | Not run yet |
| Log out or switch account during a read                                   | Not run yet |
| Revoke phone token and disable service                                    | Not run yet |
| Change PC LAN IP; reject a different TLS key at the same IP               | Not run yet |
| Deny Android local-network permission and retry                           | Not run yet |
| Confirm Windows Public profile has no inbound allow rule                  | Not run yet |

The Android client code and debug APK are built. The connected Android emulator
remained `offline` in `adb devices`, and no physical phone was attached, so
instrumentation and same-Wi-Fi acceptance have not run. The debug APK is for
review and testing; it has not been signed with an owner release key or
published as a GitHub Release. No physical-device outcome is inferred from
unit tests, APK compilation or the desktop loopback fixture.
