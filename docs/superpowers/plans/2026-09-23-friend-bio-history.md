# Friend Bio History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Show locally observed bio changes from a user's detail page, including safe old/new diff, count, and paginated history.

**Architecture:** Read the existing account-scoped `feed_bio` table by stable user ID. Extend the existing friend update capture to include transitions to/from an empty bio, extract the Feed diff formatter into a reusable safe helper, and open a lazy-loading dialog from the Info tab.

**Tech Stack:** Vue 3, Pinia, SQLite, Vitest, VRCX CEF.

**Spec:** `docs/superpowers/specs/2026-09-23-friend-bio-history-design.md`

## Global Constraints

- Store and display observation time, never claim it is the user's actual edit time.
- Use user ID and current account table prefix for every history read.
- Do not invent past edits from the current profile or refetch VRChat history.
- Preserve multiline text and escape user-supplied bio content before diff HTML rendering.
- Keep existing Feed rendering working and load history only while its dialog is open.

## Review Focus

- A cleared bio and a bio filled from blank must both appear, without counting null/missing data as a change.
- Two events with identical timestamps must paginate without duplicates or omissions.
- A stale read from a previous user/dialog must not overwrite the next user's history.
- Current bio differing from the latest saved event must show unknown change time.
- Arbitrary HTML in a bio must render as text, not markup.

---

### Task 1: Event capture and local history reads

**Files:** Modify `src/coordinators/userEventCoordinator.js`, `src/services/database/feed.js`, `src/services/database/index.js`; add focused tests under `src/coordinators/__tests__/` and `src/services/database/__tests__/`.

**Interfaces:** `database.getFriendBioHistory(userId, { cursor, limit })` returns `{ rows, nextCursor }`; `database.getFriendBioHistoryCount(userId)` returns an integer. Rows contain `id`, `observedAt`, `bio`, and `previousBio`.

- [x] Write tests for `A → B`, `'' → A`, `A → ''`, absent values, and account-scoped SQL with same-time `(created_at, id)` cursor.
- [x] Run focused Vitest tests and confirm the new assertions fail for missing behavior.
- [x] Update the friend change condition, add the index and paginated/count queries; keep timestamps as observation time.
- [x] Rerun the focused tests and verify they pass.

### Task 2: Reusable safe bio diff

**Files:** Create `src/shared/utils/bioDiff.js` and its test. Modify `src/views/Feed/columns.jsx` to use the helper.

**Interface:** `formatBioDifference(previousBio, bio)` returns escaped HTML containing the existing Feed `x-text-added` and `x-text-removed` classes.

- [x] Write tests for additions/deletions, multiline text, empty strings, and hostile HTML; run them red.
- [x] Extract/adapt the existing Feed formatter into the helper, preserving the Feed's visible diff behavior.
- [x] Run the helper and existing Feed column tests green.

### Task 3: History dialog in user details

**Files:** Create `src/components/dialogs/UserDialog/UserDialogBioHistoryDialog.vue` and its component test; modify `UserDialogInfoTab.vue`; add English and Chinese strings.

**Interface:** Dialog props `{ open, userId, displayName, currentBio }` and event `update:open`. It requests count/page data from the database and emits no network requests.

- [x] Write component tests for open/load, current bio, observed timestamp, diff, pagination, error/retry, empty state, user switch, and stale-result guard; run red.
- [x] Add the Info tab entry and dialog with lazy local loading, 50-row pages, full-text toggle, and clear labels for observation time.
- [x] Rerun component and affected Info tab tests green.

### Task 4: Final verification and Git

- [x] Run new/affected Vitest tests, lint, formatting, production frontend build, and Windows CEF build if the active executable can be kept intact.
- [x] Compare the implementation to the spec and review the five failure modes above.
- [x] Commit the plan, source, translations, and tests with a clear message; push `codex/friend-bio-history-design` to `origin`; verify remote SHA and clean worktree.

Verification note: The six directly affected Vitest files passed (19 tests). `npm run lint`, selected-file formatting, frontend production build, and self-contained Windows CEF build passed. The repository-wide test run had 2004 passes and 172 failures across other modules, including a test importing a missing `groupOrderUtils` module. A final review identified and fixed the case where unavailable profile data was displayed as an empty bio.
