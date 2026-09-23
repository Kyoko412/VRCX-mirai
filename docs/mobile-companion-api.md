# VRCX Mirai Companion API v1

The Windows CEF application serves this API over HTTPS on an explicitly selected private IPv4 interface. The phone uses the fixed TLS hostname `vrcx-companion.invalid`, resolves it to the paired LAN address, and pins the certificate's SHA-256 SPKI fingerprint. Every endpoint is `/v1/...`; a client must reject a status whose `apiVersion` is not `1`.

## Encoding, authentication, and time

- JSON is UTF-8 with camel-case names. Nullable properties are present as `null`.
- Except for pairing, send `Authorization: Bearer <device token>`; this includes `/v1/status`. The token is bound to the desktop account that approved it.
- All timestamps are UTC ISO 8601 strings such as `2026-09-24T02:15:00Z`. They denote when VRCX observed or saved a record, not an exact VRChat edit, entry, or exit time. An unknown exit is `null`.
- Every authenticated response contains the active `accountId`. The client discards in-memory pages when it changes. No data endpoint accepts an account ID, table prefix, SQL, path, or database name from the phone.
- Lists accept `limit=1..50` (default `20`) and an opaque `cursor` returned as `nextCursor`. The first page omits `cursor`; `null` means no next page. Cursors include the stable row identity after timestamp, so tied timestamps neither skip nor duplicate rows. A friend list also accepts `search` of at most 100 characters.

## Pairing (no account data)

The desktop shows a two-minute, one-use QR JSON object:

```json
{"v":1,"address":"192.168.1.10","port":34682,"host":"vrcx-companion.invalid","spkiSha256":"sha256/BASE64_PIN","secret":"BASE64URL_SECRET"}
```

The phone checks a private IPv4 address, valid port, fixed host, well-formed pin, and QR size before connecting. It verifies TLS hostname and SPKI pin for the **pairing requests too**.

`POST /v1/pair/requests` with `{"secret":"BASE64URL_SECRET","deviceName":"My phone"}` returns `201 {"requestId":"opaque-id","pollSecret":"BASE64URL_POLL_SECRET"}`. This creates a pending request on the desktop; it does not grant access. The user must approve it on the desktop. `POST /v1/pair/redeem` with `{"requestId":"opaque-id","pollSecret":"BASE64URL_POLL_SECRET"}` returns `202 {"state":"pending"}` while waiting, `200 {"deviceId":"opaque-id","token":"BASE64URL_DEVICE_TOKEN"}` once after approval, or an error on rejection/expiry/reuse. Device name is display-only and length-limited. Pairing responses never contain `accountId` or user records.

## Read endpoints

`GET /v1/status`:

```json
{"apiVersion":1,"accountId":"usr_me","computerName":"Desktop","syncState":"ready"}
```

`GET /v1/friends?search=&limit=20&cursor=` lists verified current friends that also exist in the local friend cache:

```json
{"accountId":"usr_me","items":[{"id":"usr_a","displayName":"A"}],"nextCursor":null}
```

The following three friend routes require an ID in the current verified friend set. A former or unknown friend yields `404` even if old database rows remain.

`GET /v1/friends/{id}/world-visits?limit=20&cursor=`:

```json
{"accountId":"usr_me","items":[{"eventKey":"gps:42","worldId":"wrld_a","worldName":"World A","location":"wrld_a:123","enteredAt":"2026-09-24T02:00:00Z","exitedAt":null,"durationMs":null,"observedAt":"2026-09-24T02:00:00Z","visitCount":2}],"nextCursor":null}
```

`visitCount` is computed across all locally observed visits to that world before pagination. An unproven entry or exit is `null`; duplicate source rows do not imply multiple visits.

`GET /v1/friends/{id}/encounters?limit=20&cursor=`:

```json
{"accountId":"usr_me","qualifiedCount":2,"unknownCount":1,"items":[{"visitKey":"visit:42","worldId":"wrld_a","worldName":"World A","location":"wrld_a:123","observedAt":"2026-09-24T02:00:00Z"}],"nextCursor":null}
```

Only qualified encounters appear in `items`; uncertainty remains visible in `unknownCount` and is never counted as a qualified meeting.

`GET /v1/friends/{id}/bio-history?limit=20&cursor=`:

```json
{"accountId":"usr_me","items":[{"id":42,"previousBio":"Old bio","bio":"New bio","observedAt":"2026-09-24T02:00:00Z"}],"nextCursor":null}
```

`GET /v1/me/game-log?limit=20&cursor=` returns only newly owner-tagged location sessions for the active account. Legacy global `gamelog_*` rows without proven ownership are omitted:

```json
{"accountId":"usr_me","items":[{"id":42,"createdAt":"2026-09-24T02:00:00Z","location":"wrld_a:123","worldId":"wrld_a","worldName":"World A","durationMs":600000}],"nextCursor":null}
```

## Errors and compatibility

Errors have `{"code":"invalid_cursor","message":"Invalid cursor"}`. Codes do not reveal database details or other accounts.

| HTTP | Meaning | Example code |
| --- | --- | --- |
| 400 | Malformed or out-of-range request | `invalid_request`, `invalid_cursor` |
| 401 | Missing or invalid bearer token | `unauthorized` |
| 403 | Revoked device, wrong account, or rejected pairing | `forbidden`, `pairing_rejected` |
| 404 | Non-friend or unavailable record | `not_found` |
| 409 | Account/session changed during the request | `session_changed` |
| 429 | Pairing requests exceeded the local rate limit | `rate_limited` |
| 503 | Service or database unavailable | `unavailable` |

Clients understand major version 1 only; future incompatible shapes use `/v2`. No route exposes the underlying SQLite file or a generic query facility.
