# Mutual Friend Encounters Implementation Plan

Status: Implemented. The checklist below records the original plan; verified results follow it.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Count confirmed shared-room visits only for players with at least one mutual friend, and show the count in Player List and a per-player history.

**Architecture:** Reuse observed game-log room and player events. A small tracker owns one visit at a time, checks mutual-friend counts through a bounded queue, and persists a per-visit decision keyed by player ID. The UI reads confirmed counts and immutable history from the account-specific database table.

**Tech Stack:** Vue 3, Pinia, Vitest, SQLite, VRCX CEF, VRChat API.

**Spec:** `docs/superpowers/specs/2026-09-23-mutual-friend-encounters-design.md`

## Global Constraints

- Include friends and nonfriends; exclude the current user; only mutual **friends** qualify.
- At most one decision per self room visit and player ID, including rejoin and log replay.
- A new self visit counts again even when its instance ID matches an earlier visit.
- A successful positive mutual count qualifies; successful zero does not; errors and missing IDs remain unknown.
- Preserve existing profile “见面的次数” and all historical game-log rows.
- Do not infer historical mutual status from today's relationships.

## Review Focus

- A delayed API reply after either participant leaves must not turn an unknown visit into a qualified one.
- A queued request for an old account or room must not write into a later account or room.
- A restart and game-log replay must not increment an already decided visit.
- A repeated entry to the same instance must produce a new visit key and a second qualified count.
- A 403/429 or missing stable player ID must remain unknown without blocking the player list.

---

### Task 1: Persistence and idempotent visit decisions

**Files:** Create `src/services/database/mutualEncounters.js` and its Vitest test. Modify `src/services/database/index.js` to register the table and methods.

**Interface:** `upsertMutualEncounter(candidate)` creates or reads a row keyed by `(visit_key, other_user_id)`; `setMutualEncounterDecision(key, userId, status, count, checkedAt)` updates only an unknown row; `getMutualEncounterSummaries(userIds)` and `getMutualEncounterHistory(userId)` return UI data.

- [ ] Write a failing database test proving that candidate creation uses `INSERT OR IGNORE`, the table is account-scoped, decisions cannot overwrite a prior decision, and summaries count only `qualified` rows.
- [ ] Run `npx vitest run src/services/database/__tests__/mutualEncounters.test.js` and observe the expected failure.
- [ ] Add the schema, indexes, queries, and index export; rerun that test until green.

### Task 2: Encounter state and mutual lookup

**Files:** Create `src/services/mutualEncounters/encounterTracker.js` and its Vitest test. Create `src/stores/mutualEncounters.js` as the integration adapter.

**Interface:** `enterVisit({ accountId, location, enteredAt })`, `playerJoined({ userId, displayName })`, `playerLeft(userId)`, `endVisit()`, and `restoreVisit(...)`. The adapter supplies the database methods and `userRequest.getMutualCounts`.

- [ ] Write failing tracker tests for one count per visit, same-instance re-entry, self and missing-ID exclusion, zero and unknown results, late replies, 429 retry, account switch, and replay.
- [ ] Run `npx vitest run src/services/mutualEncounters/__tests__/encounterTracker.test.js` and observe failures from missing behavior.
- [ ] Implement a bounded serial request queue, visit-scoped deduplication, result guards, and a reactive summary cache; rerun until green.

### Task 3: Connect observed room events

**Files:** Modify `src/coordinators/gameLogCoordinator.js`; add or extend focused coordinator tests.

- [ ] Write a failing test that room entry opens a visit, player join creates a candidate, player leave cancels pending qualification, and recovered player lists use the original room entry timestamp.
- [ ] Run the focused coordinator test and confirm red.
- [ ] Connect the tracker to room, join, leave, and recovery paths; honor `gameLogDisabled`; rerun until green.

### Task 4: Player List and per-player detail

**Files:** Modify `src/views/PlayerList/PlayerList.vue`, `src/views/PlayerList/columns.jsx`, `src/components/dialogs/UserDialog/UserDialog.vue`, `src/localization/en.json`, and `src/localization/zh-CN.json`. Create `src/components/dialogs/UserDialog/UserDialogMutualEncountersTab.vue` and focused component tests.

- [ ] Write failing UI tests for positive/zero/unknown badges, the confirmed total, and immutable history visible after clicking a player.
- [ ] Run the focused tests and confirm red.
- [ ] Add the localized column and detail tab without changing the existing `joinCount` field; rerun until green.

### Task 5: Verification and version control

- [ ] Run all new and affected focused tests, `npm run lint`, formatting, `npm run prod`, and the Windows CEF build using `--self-contained true`; record known baseline failures separately.
- [ ] Review the spec against implementation and test the failure modes in Review Focus.
- [ ] Commit source, tests, and plan with a clear feature message; push `codex/friend-world-visits` to `origin`; verify remote SHA and clean worktree.

## Verification record

- Added the account-scoped database table, visit deduplication, queued mutual lookup, game-log integration, Player List columns, and per-player history.
- Focused Vitest tests, `npm run lint`, `npm run prod`, and a self-contained Windows CEF x64 build passed.
- The project-wide Vue typecheck still reports existing errors; the new encounter modules do not appear in the error list.
- The Windows build is in `build/Cef-mutual-encounters/` so the running `build/Cef/` executable remains available.
